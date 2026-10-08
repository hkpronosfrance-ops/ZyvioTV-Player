"""Contract guards for Apple M3U Live, VOD and Series parity."""
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "iosApp" / "SupabaseLibrarySyncService.swift"


class AppleM3uContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.mapper = cls.source.split("private func mapM3uCatalog(", 1)[1].split("func loadCatalog(", 1)[0]
        cls.detail = cls.source.split("func loadSeriesDetail(", 1)[1].split("private struct AppleM3uEntry", 1)[0]

    def test_all_catalog_types_are_mapped(self):
        for expression in ("liveChannels.append(", "movies.append(", "series.append("):
            with self.subTest(expression=expression):
                self.assertIn(expression, self.mapper)

    def test_series_detail_dispatches_to_m3u(self):
        self.assertIn('if secret.providerType == "m3u"', self.detail)
        self.assertIn("m3uSeriesDetails(entries: entries)[seriesId]", self.detail)

    def test_real_episode_patterns_and_no_synthetic_episodes(self):
        for marker in ("[sS]", "[xX]", "[sS]aison", "guard let identity = m3uEpisode(entry.name) else { continue }"):
            with self.subTest(marker=marker):
                self.assertIn(marker, self.source)

    def test_xmltv_id_and_parental_filtering_remain(self):
        self.assertIn("epgId: entry.tvgId", self.mapper)
        for marker in ("filteredLive", "filteredMovies", "filteredSeries"):
            self.assertIn(marker, self.source)


if __name__ == "__main__":
    unittest.main()

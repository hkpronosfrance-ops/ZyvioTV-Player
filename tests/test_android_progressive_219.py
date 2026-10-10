"""PR #219 — channels and films first, provider URL kind first (static contract)."""

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/fr/zyviotv/player"


def read(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8")


class AndroidProgressive219Test(unittest.TestCase):
    def test_early_catalogue_only_on_a_first_sync_with_parental_rules(self):
        session = read(APP / "ui/catalog/ProviderCatalogSession.kt")
        self.assertIn("val earlyLocks = if (previousReady == null) {", session)
        self.assertIn("val onEarlyCatalog: ((String, String, CatalogSnapshot) -> Unit)? = earlyLocks?.let", session)
        early = session.split("val onEarlyCatalog", 1)[1].split("val outcome =", 1)[0]
        self.assertIn("applyParentalCatalogPolicy(snapshot = partial, locks = earlyRules)", early)
        self.assertIn("seriesPending = true", early)
        self.assertIn('name = "catalog_early_ready"', early)

    def test_early_catalogue_needs_the_family_order_proof(self):
        session = read(APP / "ui/catalog/ProviderCatalogSession.kt")
        self.assertIn("!earlyPublished && onEarlyCatalog != null && builder.liveAndMoviesComplete", session)
        self.assertIn('name = "m3u_family_order"', session)
        mapper = read(APP / "data/catalog/M3uCatalogMapper.kt")
        complete = mapper.split("val liveAndMoviesComplete: Boolean", 1)[1].split("fun add(", 1)[0]
        for condition in ("seriesPhaseStarted", "orderViolations == 0", "episodesSinceFirst >= EARLY_EPISODE_RUN"):
            self.assertIn(condition, complete)
        # The early snapshot never mutates the builder and carries no series.
        early = mapper.split("fun buildLiveAndMovies()", 1)[1].split("fun build()", 1)[0]
        self.assertIn("series = emptyList()", early)
        self.assertNotIn("M3uSeriesDetailRegistry", early)
        self.assertNotIn("idAliases =", early)

    def test_a_failure_after_the_early_catalogue_keeps_channels_and_films(self):
        session = read(APP / "ui/catalog/ProviderCatalogSession.kt")
        self.assertIn("} else if (session.earlyReady != null) {", session)
        adapters = read(APP / "ui/catalog/CatalogUiAdapters.kt")
        self.assertIn("La synchronisation des séries n’a pas abouti. Réessayez.", adapters)
        self.assertIn("SeriesScreenState.Loading", adapters.split("fun ProviderCatalogState.toSeriesState(", 1)[1])

    def test_the_provider_url_kind_is_checked_before_group_words(self):
        mapper = read(APP / "data/catalog/M3uCatalogMapper.kt")
        classify = mapper.split("private fun classify(", 1)[1].split("private fun parseEpisode(", 1)[0]
        self.assertLess(classify.index('"/movie/"'), classify.index("MOVIE_TOKENS"))
        self.assertLess(classify.index("XTREAM_LIVE_PATH"), classify.index("LIVE_TOKENS"))
        self.assertLess(classify.index('"/series/"'), classify.index("MOVIE_TOKENS"))
        self.assertIn("episode != null && isFile", classify)


if __name__ == "__main__":
    unittest.main()

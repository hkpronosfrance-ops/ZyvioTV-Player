"""PR #215: startup read speed and system splash colour.

Pixel 7 recette of PR B (10/10/2026): catalog_ready took 32 s; the cause
found in the code was one String.format call per byte while building the
source references of 18 681 items. The Android 12+ system splash was also
white instead of the validated ZYVIOTV black.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main"
STORE = APP / "java/fr/zyviotv/player/data/store"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class AndroidCatalogReady215Test(unittest.TestCase):
    def test_source_references_never_format_bytes(self):
        ref = read(STORE / "CatalogSourceRef.kt")
        self.assertNotIn(".format(", ref)
        self.assertIn("fun prefix(kind: CatalogKind, generationId: Long, playlistId: String)", ref)

    def test_reader_builds_each_prefix_once(self):
        reader = read(STORE / "CatalogStoreReader.kt")
        load = reader.split("private fun load(", 1)[1].split("    /** Episodes of one series", 1)[0]
        self.assertEqual(load.count("CatalogSourceRef.prefix("), 2)
        self.assertEqual(load.count("CatalogSourceRef.ofItem("), 2)
        self.assertNotIn(".encode()", load)

    def test_system_splash_is_zyviotv_black(self):
        theme = read(APP / "res/values-v31/themes.xml")
        self.assertIn('<item name="android:windowSplashScreenBackground">#050506</item>', theme)
        base = read(APP / "res/values/themes.xml")
        self.assertIn('<item name="android:windowBackground">#080808</item>', base)
        self.assertIn('<item name="android:windowBackground">#080808</item>', theme)

    def test_ci_runs_these_contracts(self):
        workflow = read(ROOT / ".github/workflows/android-ci.yml")
        self.assertIn("test_android_catalog_ready_215.py", workflow)


if __name__ == "__main__":
    unittest.main()

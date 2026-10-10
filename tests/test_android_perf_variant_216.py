"""PR #216: a measurement build close to release.

The Pixel 7 recettes ran the debug build from Android Studio: no AOT, runtime
class verification ("Verification of ... took"), JVMTI agent attached. The
"perf" build type reuses the release configuration (R8, not debuggable),
signed with the local debug key, so startup and UI speed can be measured
without a publishing key. It is installed next to the debug app.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class AndroidPerfVariant216Test(unittest.TestCase):
    def test_perf_build_type_copies_release_and_uses_the_debug_key(self):
        gradle = read(ROOT / "app/build.gradle.kts")
        block = gradle.split('create("perf") {', 1)[1].split("}", 1)[0]
        self.assertIn('initWith(getByName("release"))', block)
        self.assertIn('applicationIdSuffix = ".perf"', block)
        self.assertIn('signingConfig = signingConfigs.getByName("debug")', block)
        self.assertNotIn("isDebuggable = true", block)
        self.assertNotIn("isMinifyEnabled = false", block)

    def test_release_build_is_not_signed_with_the_debug_key(self):
        gradle = read(ROOT / "app/build.gradle.kts")
        release = gradle.split("release {", 1)[1].split("}", 1)[0]
        self.assertNotIn("signingConfig", release)

    def test_perf_app_has_its_own_launcher_name(self):
        for folder in ("values", "values-en"):
            strings = read(ROOT / f"app/src/perf/res/{folder}/strings.xml")
            self.assertIn('<string name="app_name">ZyvioTV Perf</string>', strings)

    def test_ci_builds_the_perf_apk_and_runs_these_contracts(self):
        workflow = read(ROOT / ".github/workflows/android-ci.yml")
        self.assertIn("gradle assemblePerf --stacktrace", workflow)
        self.assertIn("test_android_perf_variant_216.py", workflow)


if __name__ == "__main__":
    unittest.main()

"""D7 source-level checks only: these do not certify real device behavior."""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCREENS = {
    "tizenApp": ["auth-screen", "profile-screen", "pin-screen", "system-screen", "app-shell", "catalog-panel", "devices-panel"],
    "webosApp": ["auth-screen", "profile-screen", "pin-screen", "system-screen", "app-shell", "live-panel", "catalog-panel", "devices-panel"],
}
class D7FinalQASourceTests(unittest.TestCase):
    def test_tv_screen_identity_and_navigation(self):
        for platform, screens in SCREENS.items():
            with self.subTest(platform=platform):
                html = (ROOT / platform / "index.html").read_text()
                for screen in screens:
                    self.assertIn('id="' + screen + '"', html)
                for section in ("home", "live", "movies", "series", "profiles", "more"):
                    self.assertIn('data-section="' + section + '"', html)
    def test_tv_safe_focus_and_motion(self):
        for platform in SCREENS:
            with self.subTest(platform=platform):
                css = (ROOT / platform / "styles.css").read_text()
                self.assertIn("#F4F4F6", css)
                self.assertRegex(css, r"outline:\s*4px")
                self.assertIn("prefers-reduced-motion", css)
                self.assertIn("max-width:1280px", css)
                self.assertIn("min-width:2560px", css)
    def test_sources_and_streams_not_removed(self):
        for platform in SCREENS:
            with self.subTest(platform=platform):
                html = (ROOT / platform / "index.html").read_text()
                for script in ("provider.js", "player.js", "cloud.js"):
                    self.assertIn('src="' + script + '"', html)
                provider = (ROOT / platform / "provider.js").read_text()
                self.assertTrue(any(word in provider for word in ("loadMovies", "loadSeries")))
    def test_webos_section_hides_stale_devices(self):
        app = (ROOT / "webosApp" / "app.js").read_text()
        start = app.index("function hidePanels()")
        end = app.index("function activate(section)", start)
        self.assertIn("devicesPanel.hidden = true", app[start:end])
    def test_release_evidence_not_marked_pass_without_hardware(self):
        report = (ROOT / "docs" / "d7-release-evidence-193.md").read_text()
        self.assertIn("NON EXÉCUTÉ", report)
        self.assertIn("P04", report)
        self.assertIn("P16", report)
if __name__ == "__main__":
    unittest.main()

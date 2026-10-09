import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

class D6CrossPlatformPolishTests(unittest.TestCase):
    def test_tv_shared_focus_and_overscan(self):
        for platform in ("tizenApp", "webosApp"):
            with self.subTest(platform=platform):
                css = (ROOT / platform / "styles.css").read_text()
                self.assertIn("#F4F4F6", css)
                self.assertIn("prefers-reduced-motion", css)
                self.assertIn("max-width:1280px", css)
                self.assertIn("min-width:2560px", css)
                self.assertIn("overflow-x:auto", css.replace(" ", ""))
    def test_ios_adaptive_posters_and_accessibility(self):
        for screen in ("MoviesView.swift", "SeriesView.swift"):
            with self.subTest(screen=screen):
                code = (ROOT / "iosApp" / screen).read_text()
                self.assertIn("GridItem(.adaptive(", code)
                self.assertIn(".accessibilityLabel(", code)
                self.assertIn(".accessibilityHint(", code)
    def test_playback_and_provider_modules_still_linked(self):
        for platform in ("tizenApp", "webosApp"):
            html = (ROOT / platform / "index.html").read_text()
            for name in ("provider.js", "player.js"):
                self.assertIn('src="' + name + '"', html)

if __name__ == "__main__":
    unittest.main()

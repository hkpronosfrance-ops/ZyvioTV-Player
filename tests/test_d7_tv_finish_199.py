import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
class D7TVFinishTests(unittest.TestCase):
    def test_focus_visibility_motion_and_overscan(self):
        for platform in ("tizenApp", "webosApp"):
            with self.subTest(platform=platform):
                css = (ROOT/platform/"styles.css").read_text()
                self.assertIn("button:focus-visible",css)
                self.assertIn("input:focus-visible",css)
                self.assertIn("outline:4px solid #F4F4F6",css)
                self.assertIn("@media (max-width:1280px)",css)
                self.assertIn("@media (prefers-reduced-motion: reduce)",css)
                self.assertIn("animation:none!important",css)
if __name__ == "__main__":
    unittest.main()

import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

class TvNavigationInputTests(unittest.TestCase):
    def test_tizen_input_editing_preserves_arrows_and_backspace(self):
        source = (ROOT / "tizenApp" / "app.js").read_text()
        self.assertIn('const editingText = ["INPUT", "TEXTAREA"].includes(document.activeElement?.tagName);', source)
        self.assertIn('if (direction && !editingText)', source)
        self.assertIn('(event.key === "Backspace" && !editingText)', source)
        self.assertIn('event.keyCode === 10009', source)
    def test_webos_input_editing_preserves_arrows_and_backspace(self):
        source = (ROOT / "webosApp" / "app.js").read_text()
        self.assertIn('!["INPUT", "TEXTAREA"].includes(document.activeElement?.tagName)', source)
        self.assertIn('event.keyCode === 461', source)

if __name__ == "__main__":
    unittest.main()

"""D7 cross-platform static design gates (not a substitute for device QA)."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def test_tv_safe_areas_focus_visibility_and_reduced_motion():
    for relative in ("tizenApp/styles.css", "webosApp/styles.css"):
        css = (ROOT / relative).read_text()
        assert "--zyvio-tv-safe: 32px" in css, relative
        assert "--zyvio-tv-safe: 20px" in css, relative
        assert "--zyvio-tv-safe: 48px" in css, relative
        assert "padding: 12px 10px 20px" in css, relative
        assert "outline: 4px solid var(--zyvio-focus-ring)" in css, relative
        assert "prefers-reduced-motion: reduce" in css, relative

def test_qa_matrix_is_explicit_about_uncertified_device_behavior():
    doc = (ROOT / "docs/claude-d7-crossplatform-acceptance.md").read_text()
    for term in ("NOT certified", "R-01", "R-02", "R-03", "R-04", "R-05", "R-06",
                 "M3U Live/Movie/Series", "Xtream Live/Movie/Series",
                 "Tizen", "webOS", "iPhone/iPad", "Android TV"):
        assert term in doc, term

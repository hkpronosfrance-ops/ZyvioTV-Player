"""Claude Design D6 TV remote-control focus interaction guards."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_tizen_and_webos_have_consistent_remote_focus():
    for platform in ("tizenApp", "webosApp"):
        css = (ROOT / platform / "styles.css").read_text()
        assert "outline: 4px solid var(--zyvio-focus-ring)" in css, platform
        assert "outline-offset: 4px" in css, platform
        assert "--zyvio-focus-ring: #F4F4F6" in css, platform
        assert "transform: scale(1.02)" in css, platform
        assert "prefers-reduced-motion: reduce" in css, platform
        assert "transform: none !important" in css, platform


def test_d6_pressed_and_disabled_component_states():
    for platform in ("tizenApp", "webosApp"):
        css = (ROOT / platform / "styles.css").read_text()
        assert "--zyvio-pressed: #B30C25" in css
        assert "opacity: .4" in css
        assert "transform: scale(.97)" in css

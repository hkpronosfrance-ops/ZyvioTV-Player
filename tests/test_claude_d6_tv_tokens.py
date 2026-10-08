"""Claude Design D6 TV foundation regression tests (Tizen and webOS)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_tv_platforms_share_d6_tokens_and_white_focus():
    for platform in ("tizenApp", "webosApp"):
        css = (ROOT / platform / "styles.css").read_text()
        for token in ("--zyvio-bg: #050506", "--zyvio-surface-1: #121215",
                      "--zyvio-red: #E0102F", "--zyvio-text: #F4F4F6",
                      "--zyvio-focus: #F4F4F6"):
            assert token in css, (platform, token)
        assert ":focus-visible, [data-focusable]:focus" in css, platform
        assert "@media (prefers-reduced-motion: reduce)" in css, platform
        assert "font-family:Manrope" in css or "font-family: Manrope" in css, platform

"""D6 Samsung TV ten-foot design regression guards."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def test_samsung_tv_adaptive_sidebar_and_hero():
    css=(ROOT/"tizenApp/styles.css").read_text()
    assert "grid-template-columns: clamp(188px, 16vw, 268px)" in css
    assert "min-height: clamp(270px, 34vh, 470px)" in css
    assert ".nav-item.active" in css and "border-left: 4px solid var(--zyvio-red)" in css
    assert "scroll-snap-type: x proximity" in css
def test_samsung_tv_4k_720p_and_remote_focus():
    css=(ROOT/"tizenApp/styles.css").read_text()
    assert "@media (max-width: 1280px)" in css
    assert "@media (min-width: 2560px)" in css
    assert "outline: 4px solid var(--zyvio-focus-ring)" in css
    assert "@media (prefers-reduced-motion: reduce)" in css
def test_samsung_tv_landmarks_and_existing_actions():
    html=(ROOT/"tizenApp/index.html").read_text()
    assert 'aria-label="Rubriques ZYVIOTV"' in html
    assert 'aria-current="page"' in html
    assert 'aria-label="Découvrir ZYVIOTV"' in html
    for section in ("home","live","movies","series","profiles","more"):
        assert 'data-section="'+section+'"' in html
    assert 'data-action="open-live"' in html

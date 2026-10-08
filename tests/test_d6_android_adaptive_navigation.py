"""D6 adaptive Android navigation regression tests."""
from pathlib import Path

UI = Path(__file__).resolve().parents[1] / "app/src/main/java/fr/zyviotv/player/ui/ZyvioTVPlayerApp.kt"


def test_mobile_navigation_has_d6_surface_and_selection():
    source = UI.read_text()
    assert "NavigationBar(" in source
    assert "containerColor = ZyvioSurface1" in source
    assert "NavigationBarItemDefaults.colors(" in source
    assert "indicatorColor = ZyvioRedTint" in source


def test_tablet_tv_navigation_has_d6_surface_and_selection():
    source = UI.read_text()
    assert "NavigationRail(" in source
    assert "NavigationRailItemDefaults.colors(" in source
    assert "Row(Modifier.fillMaxSize().background(ZyvioCanvas))" in source
    assert "horizontal = if (profile == DeviceProfile.Television) ZyvioSpace.s12 else ZyvioSpace.s6" in source


def test_tv_remote_focus_remains_enabled():
    source = UI.read_text()
    assert "Modifier.tvFocusEffect(" in source
    assert "enabled = profile == DeviceProfile.Television" in source

"""Claude D6 design adoption in actual Android player, navigation and profiles."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/java/fr/zyviotv/player/ui"


def test_android_navigation_uses_d6_spacing():
    code = (ROOT / "ZyvioTVPlayerApp.kt").read_text()
    assert "import fr.zyviotv.player.ui.theme.ZyvioSpace" in code
    assert ".padding(ZyvioSpace.s4)" in code


def test_android_player_uses_canonical_d6_surface():
    code = (ROOT / "player/PlayerScreen.kt").read_text()
    assert "import fr.zyviotv.player.ui.theme.ZyvioBase" in code
    assert "import fr.zyviotv.player.ui.theme.ZyvioSpace" in code
    assert "Column(Modifier.padding(ZyvioSpace.s6))" in code
    assert "Color(0xFF05090C)" not in code


def test_profiles_apply_d6_grid():
    code = (ROOT / "settings/ProfilesSettingsScreen.kt").read_text()
    assert "Column(Modifier.padding(ZyvioSpace.s4))" in code

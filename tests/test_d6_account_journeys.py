"""D6 account journey consistency without changing profile/playlist/device behavior."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / "app/src/main/java/fr/zyviotv/player/ui"
SCREENS = (
    "auth/AuthScreen.kt",
    "settings/ProfilesSettingsScreen.kt",
    "settings/PlaylistSettingsScreen.kt",
    "settings/DevicesSettingsScreen.kt",
)

def test_account_screens_use_shared_d6_shape_tokens():
    for relative in SCREENS:
        source = (ROOT / relative).read_text()
        assert "import fr.zyviotv.player.ui.theme.ZyvioRadius" in source, relative
        assert "RoundedCornerShape(ZyvioRadius." in source, relative

def test_settings_keep_remote_focus_and_cloud_loading():
    for relative in SCREENS[1:]:
        source = (ROOT / relative).read_text()
        assert ".tvFocusEffect(" in source, relative
        assert "LaunchedEffect(reloadToken)" in source, relative
        assert "CircularProgressIndicator()" in source, relative

def test_auth_keeps_existing_inputs():
    source = (ROOT / "auth/AuthScreen.kt").read_text()
    assert "RoundedCornerShape(ZyvioRadius.md)" in source

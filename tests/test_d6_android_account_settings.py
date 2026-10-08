"""D6 account and settings visual-grid adoption across Android and iOS."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "app/src/main/java/fr/zyviotv/player/ui"


def test_account_and_settings_reference_shared_grid():
    for relative in ("auth/AuthScreen.kt", "settings/PlaylistSettingsScreen.kt",
                     "settings/DevicesSettingsScreen.kt", "settings/ProfilesSettingsScreen.kt"):
        source = (ANDROID / relative).read_text()
        assert "import fr.zyviotv.player.ui.theme.ZyvioSpace" in source, relative
        assert "Modifier.padding(ZyvioSpace.s4)" in source or "ZyvioSpace.s6" in source, relative
        assert "Modifier.padding(14.dp)" not in source, relative


def test_form_spacing_has_cross_platform_token_parity():
    android = (ANDROID / "theme/ZyvioDesignTokens.kt").read_text()
    apple = (ROOT / "iosApp/ZyvioDesignTokens.swift").read_text()
    assert "val s7 = 28.dp" in android
    assert "let s7: CGFloat = 28" in apple
    assert ".padding(horizontal = ZyvioSpace.s7, vertical = ZyvioSpace.s8)" in (ANDROID / "auth/AuthScreen.kt").read_text()

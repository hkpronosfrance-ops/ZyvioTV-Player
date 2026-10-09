"""D6 responsive account and authentication screen contracts."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
def test_android_account_scrollable_and_full_width_capped():
    s=(ROOT/"app/src/main/java/fr/zyviotv/player/ui/settings/AccountSettingsScreen.kt").read_text()
    assert ".verticalScroll(rememberScrollState())" in s
    assert "Modifier.fillMaxWidth().widthIn(max = 480.dp)" in s
    assert "onOpenPlaylists" in s and "onOpenDevices" in s
    assert "onOpenProfiles" in s and "onOpenParentalControls" in s
    assert "repository.signOut()" in s

def test_ios_account_screen_scrolls_and_preserves_account_routes():
    s=(ROOT/"iosApp/MainTabView.swift").read_text()
    a=s.index("private struct AccountView: View {")
    z=s.index("private enum SearchPlaybackTarget",a)
    code=s[a:z]
    assert "ScrollView {" in code
    assert ".frame(maxWidth: 720)" in code
    for route in ("PlaylistSettingsView(", "DevicesSettingsView(", "ParentalSettingsView(", "LibraryView(", "onSwitchProfile()"):
        assert route in code

def test_ios_auth_screen_keeps_login_modes_and_keyboard_dismiss():
    s=(ROOT/"iosApp/AuthView.swift").read_text()
    assert ".scrollDismissesKeyboard(.interactively)" in s
    assert ".background(ZyvioDesign.Palette.surface1)" in s
    for token in ("case signIn", "case signUp", "case reset", "SupabaseAuthService.shared.signIn("):
        assert token in s

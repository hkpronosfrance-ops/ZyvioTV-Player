"""D6 premium playback controls: adaptive panels and Apple error overlays."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def test_android_tracks_drawer_does_not_assume_fixed_tv_width():
    s=(ROOT/"app/src/main/java/fr/zyviotv/player/ui/player/PlayerScreen.kt").read_text()
    assert "LocalConfiguration.current.screenWidthDp.dp" in s
    assert "(availableWidth * 0.42f).coerceAtMost(520.dp)" in s
    assert "start = sidePanelOffset" in s
    assert "onSelectAudioTrack(track)" in s
    assert "onSelectSubtitleTrack(track)" in s

def test_android_resume_panel_fits_mobile_and_tv():
    s=(ROOT/"app/src/main/java/fr/zyviotv/player/ui/player/PlayerScreen.kt").read_text()
    assert ".fillMaxWidth(0.94f)" in s
    assert ".widthIn(max = if (profile == DeviceProfile.Television) 520.dp else 400.dp)" in s
    assert "onRestartFromBeginning" in s and "onResumePlayback" in s

def test_both_apple_players_center_error_panel_and_expose_close_action():
    for name in ("MoviesView.swift","SeriesView.swift"):
        s=(ROOT/"iosApp"/name).read_text()
        marker="struct MoviePlayerScreen: View" if name.startswith("Movies") else "private struct EpisodePlayerScreen: View"
        block=s[s.index(marker):]
        assert '.accessibilityLabel(Locale.current.language.languageCode?.identifier == "fr" ? "Fermer le lecteur" : "Close player")' in block
        assert ".frame(maxWidth: 440)" in block
        assert ".frame(maxWidth: .infinity, maxHeight: .infinity)" in block
        assert "ParentalProtectedPlayerView(" in block

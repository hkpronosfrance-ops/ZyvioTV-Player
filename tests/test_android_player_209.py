"""Phase #209: Android player navigation, Media3 lifecycle and controls.

Source contracts for the Pixel 7 findings: the top Retour button had no
visible effect, Media3 lifecycle edge cases, controls wrapping vertically on
portrait phones, and the HEVC decoder failure on the emulator.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PLAYER = ROOT / "app/src/main/java/fr/zyviotv/player/ui/player"
APP = ROOT / "app/src/main/java/fr/zyviotv/player/ui/ZyvioTVPlayerApp.kt"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class AndroidPlayer209Test(unittest.TestCase):
    def test_retour_and_system_back_share_one_path(self):
        host = read(PLAYER / "PlayerHost.kt")
        self.assertIn("BackHandler(onBack = handleBack)", host)
        self.assertIn("onBack = handleBack,", host)
        self.assertIn("PlayerBackPolicy.onBack(panel)", host)
        # Progress is saved before leaving, whichever back is used.
        exit_branch = host.split("PlayerBackAction.ExitPlayer ->", 1)[1].split("}", 1)[0]
        self.assertLess(exit_branch.index("onPlaybackExit"), exit_branch.index("onBack()"))

    def test_player_route_pops_exactly_once(self):
        app = read(APP)
        route = app.split("composable(PLAYER_ROUTE)", 1)[1].split("onOpenGuide", 1)[0]
        self.assertNotIn("playbackRequest = null", route)
        self.assertEqual(route.count("PlayerExitNavigation.shouldPop"), 2)

    def test_controls_respect_system_insets(self):
        screen = read(PLAYER / "PlayerScreen.kt")
        overlay = screen.split("private fun PlayerControlsOverlay", 1)[1].split("private fun PlayerAction", 1)[0]
        self.assertIn(".windowInsetsPadding(WindowInsets.safeDrawing)", overlay)
        self.assertIn('contentDescription = "Retour"', overlay)
        self.assertIn("Modifier.weight(1f)", overlay)
        self.assertIn("horizontalScroll", overlay)
        self.assertIn("iconOnly = layout.iconOnlyActions", overlay)
        # Films have nothing to advance to: no silent "Suivant" button.
        self.assertNotIn('else "Suivant"', overlay)

    def test_media3_is_released_once_per_player(self):
        native = read(PLAYER / "NativeVideoPlayer.kt")
        self.assertEqual(native.count("player.release()"), 1)
        release_effect = native.split("DisposableEffect(player) {", 1)[1].split("AndroidView(", 1)[0]
        self.assertIn("player.release()", release_effect)
        self.assertIn('PlaybackDiagnostics.released("dispose")', release_effect)
        self.assertIn("onRelease = { view -> view.player = null }", native)

    def test_lifecycle_and_commands(self):
        native = read(PLAYER / "NativeVideoPlayer.kt")
        self.assertIn("PlayerCommandGate(commandToken)", native)
        self.assertIn("commandGate.accept(commandToken)", native)
        lifecycle = native.split("DisposableEffect(player, lifecycleOwner)", 1)[1].split("DisposableEffect(player) {", 1)[0]
        self.assertIn("resumePolicy.onStart()", lifecycle)
        self.assertNotIn("player.release()", lifecycle)

    def test_video_keeps_its_proportions(self):
        native = read(PLAYER / "NativeVideoPlayer.kt")
        self.assertIn("AspectRatioFrameLayout.RESIZE_MODE_FIT", native)
        self.assertIn("AspectRatioFrameLayout.RESIZE_MODE_ZOOM", native)
        self.assertNotIn("RESIZE_MODE_FILL", native)

    def test_tracks_are_selected_by_id(self):
        native = read(PLAYER / "NativeVideoPlayer.kt")
        self.assertIn("TrackSelectionOverride(group.mediaTrackGroup, trackIndex)", native)
        host = read(PLAYER / "PlayerHost.kt")
        self.assertIn("selectedAudioTrackId = track.id", host)
        self.assertIn("selectedSubtitleTrackId = track.id", host)

    def test_hevc_diagnosis_and_decoder_fallback(self):
        native = read(PLAYER / "NativeVideoPlayer.kt")
        self.assertIn("setEnableDecoderFallback(true)", native)
        self.assertIn("decoder = error.decoderDiagnosis()", native)
        classifier = read(PLAYER / "PlaybackErrorClassifier.kt")
        self.assertIn("isLikelyEmulatorLimitation", classifier)

    def test_fullscreen_restores_the_window(self):
        fullscreen = read(PLAYER / "PlayerFullscreen.kt")
        self.assertIn("controller.show(WindowInsetsCompat.Type.systemBars())", fullscreen)
        self.assertIn("activity.requestedOrientation = previousOrientation", fullscreen)

    def test_no_stream_url_in_player_logs(self):
        for name in ("NativeVideoPlayer.kt", "PlayerHost.kt", "PlayerScreen.kt", "PlayerFullscreen.kt", "PlayerLifecycle.kt"):
            source = read(PLAYER / name)
            for line in source.splitlines():
                if "Log." in line or "PlaybackDiagnostics." in line:
                    self.assertNotIn("streamUrl", line, f"{name}: {line.strip()}")


if __name__ == "__main__":
    unittest.main()

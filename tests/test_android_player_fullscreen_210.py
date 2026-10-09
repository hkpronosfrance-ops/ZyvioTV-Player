"""PR #210: Android player full screen, orientation and Media3 teardown.

Pixel 7 finding after #209: no full screen button was visible. The player
route has no Surface above it, so LocalContentColor was Compose's default
black and the untinted full screen IconButton was black on black.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PLAYER = ROOT / "app/src/main/java/fr/zyviotv/player/ui/player"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class AndroidPlayerFullscreen210Test(unittest.TestCase):
    def test_player_provides_a_light_content_colour(self):
        screen = read(PLAYER / "PlayerScreen.kt")
        body = screen.split("fun PlayerScreen(", 1)[1].split("private fun VideoSurfacePlaceholder", 1)[0]
        self.assertIn("CompositionLocalProvider(LocalContentColor provides ZyvioTextPrimary)", body)

    def test_fullscreen_button_is_visible_and_never_scrolled_away(self):
        screen = read(PLAYER / "PlayerScreen.kt")
        overlay = screen.split("private fun PlayerControlsOverlay", 1)[1].split("private fun FullscreenButton", 1)[0]
        scroll = overlay.index(".horizontalScroll(rememberScrollState())")
        button = overlay.index("FullscreenButton(")
        self.assertGreater(button, scroll)
        # Outside the scrolling row: the row closes before the button.
        self.assertIn("if (layout.showFullscreenToggle) {\n                    Spacer(Modifier.width(8.dp))\n                    FullscreenButton(", overlay)
        # Not limited to one kind of content.
        self.assertNotIn("PlaybackKind", overlay[button - 200:button])
        fullscreen = screen.split("private fun FullscreenButton", 1)[1].split("private fun PlayerAction", 1)[0]
        self.assertIn("Icons.Default.Fullscreen", fullscreen)
        self.assertIn("Icons.Default.FullscreenExit", fullscreen)
        self.assertIn('"Plein écran"', fullscreen)
        self.assertIn('"Quitter le plein écran"', fullscreen)
        self.assertIn("tint = ZyvioTextPrimary", fullscreen)
        self.assertIn("minHeight = 48.dp", fullscreen)

    def test_fullscreen_drives_window_and_orientation(self):
        host = read(PLAYER / "PlayerHost.kt")
        self.assertIn("PlayerFullscreenPolicy.toggle(", host)
        self.assertIn("onToggleFullscreen = toggleFullscreen", host)
        self.assertIn("PlayerWindowEffect(immersive = immersive, orientation = orientationRequest)", host)
        self.assertIn("smallestScreenWidthDp", host)
        self.assertIn("PlayerBackAction.ExitFullscreen -> toggleFullscreen()", host)
        window = read(PLAYER / "PlayerFullscreen.kt")
        self.assertIn("SCREEN_ORIENTATION_SENSOR_LANDSCAPE", window)
        self.assertIn("controller.hide(WindowInsetsCompat.Type.systemBars())", window)
        self.assertIn("activity.requestedOrientation = openingOrientation", window)

    def test_rotation_does_not_recreate_the_activity(self):
        manifest = read(ROOT / "app/src/main/AndroidManifest.xml")
        for change in ("orientation", "screenSize", "screenLayout", "smallestScreenSize"):
            self.assertIn(change, manifest.split("android:configChanges=", 1)[1].split('"', 2)[1].split("|"))

    def test_surface_is_detached_before_release(self):
        native = read(PLAYER / "NativeVideoPlayer.kt")
        teardown = native.split("onDispose {\n            bufferingJob?.cancel()", 1)[1]
        self.assertLess(teardown.index("?.player = null"), teardown.index("player.release()"))
        self.assertEqual(native.count("player.release()"), 1)

    def test_films_still_have_no_next_button(self):
        screen = read(PLAYER / "PlayerScreen.kt")
        self.assertNotIn('else "Suivant"', screen)


if __name__ == "__main__":
    unittest.main()

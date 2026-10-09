package fr.zyviotv.player.ui.player

import fr.zyviotv.player.ui.DeviceProfile

/**
 * Pure decisions behind the player's navigation and lifecycle (phase #209),
 * kept out of Compose so they run in JVM tests.
 */
enum class PlayerBackAction {
    /** A panel (tracks, resume prompt) is open: close it, stay in the player. */
    ClosePanel,

    /** A channel number is being typed: cancel it. */
    CancelChannelNumber,

    /** Full screen is on: leave it first, stay in the player (#210). */
    ExitFullscreen,

    /** Leave the player: save progress, release Media3, pop the route once. */
    ExitPlayer,
}

object PlayerBackPolicy {
    /** The top-left button and the Android system back share this decision. */
    fun onBack(panel: PlayerPanel, immersive: Boolean = false): PlayerBackAction = when (panel) {
        PlayerPanel.None -> if (immersive) PlayerBackAction.ExitFullscreen else PlayerBackAction.ExitPlayer
        PlayerPanel.ChannelNumber -> PlayerBackAction.CancelChannelNumber
        PlayerPanel.Tracks,
        PlayerPanel.Resume,
        -> PlayerBackAction.ClosePanel
    }
}

object PlayerExitNavigation {
    /**
     * Pop only while the player is the current destination: a second tap on
     * Retour, or a recomposition of the exiting player entry, must never pop
     * the screen underneath it.
     */
    fun shouldPop(currentRoute: String?, playerRoute: String): Boolean = currentRoute == playerRoute
}

/**
 * Commands are delivered as (command, token) pairs. A token is executed once,
 * by the player that existed when it was sent: a new player created for the
 * next channel or episode must not replay the previous Pause/Retry.
 */
class PlayerCommandGate(initialToken: Long) {
    private var lastHandledToken = initialToken

    fun accept(token: Long): Boolean {
        if (token <= lastHandledToken) return false
        lastHandledToken = token
        return true
    }
}

/**
 * Background/foreground: resume on ON_START only if playback was running when
 * the app went to the background (a user pause survives the round trip).
 */
class PlayerResumePolicy {
    private var resumeOnStart = false

    fun onStop(wasPlaying: Boolean) {
        resumeOnStart = wasPlaying
    }

    fun onStart(): Boolean = resumeOnStart.also { resumeOnStart = false }
}

/** Layout decisions for the overlay controls, from the available size. */
data class PlayerControlsLayout(
    /** Secondary actions show their icon only (label as content description). */
    val iconOnlyActions: Boolean,
    val isLandscape: Boolean,
    val showFullscreenToggle: Boolean,
) {
    companion object {
        fun of(profile: DeviceProfile, widthDp: Int, heightDp: Int): PlayerControlsLayout =
            PlayerControlsLayout(
                iconOnlyActions = profile != DeviceProfile.Television && widthDp < LABELLED_ACTIONS_MIN_WIDTH_DP,
                isLandscape = widthDp > heightDp,
                showFullscreenToggle = profile != DeviceProfile.Television,
            )

        /** Below this width, labelled buttons wrap letter by letter. */
        const val LABELLED_ACTIONS_MIN_WIDTH_DP = 600
    }
}

/** How the video fills the surface. Neither mode ever distorts the image. */
enum class PlayerScaleMode {
    /** Whole picture visible, letterboxed (default). */
    Fit,

    /** Fills the screen, cropping the edges; proportions kept. */
    Zoom,
    ;

    fun toggled(): PlayerScaleMode = if (this == Fit) Zoom else Fit
}

/** Orientation the player asks the activity for (#210). */
enum class PlayerOrientationRequest {
    /** Whatever the activity had before the player opened (sensor/user setting). */
    FollowDevice,

    /** Full screen entered with the button: landscape, either way up. */
    Landscape,

    /** Full screen left while the phone was sideways: back to portrait. */
    Portrait,
}

/** Window state of the player, kept across rotation. */
data class PlayerWindowState(
    val userFullscreen: Boolean = false,
    val orientation: PlayerOrientationRequest = PlayerOrientationRequest.FollowDevice,
)

/**
 * Full screen on phones and tablets (#210). One rule for the button, the
 * rotation of the phone and Back:
 * - the button enters full screen (bars hidden, landscape);
 * - a phone turned sideways is full screen too, like any video player;
 * - leaving full screen while sideways asks for portrait, so the button
 *   always has a visible effect;
 * - TV keeps its own window and is never changed.
 */
object PlayerFullscreenPolicy {
    fun isImmersive(
        state: PlayerWindowState,
        isTelevision: Boolean,
        isCompactDevice: Boolean,
        isLandscape: Boolean,
    ): Boolean = !isTelevision &&
        (state.userFullscreen || (isCompactDevice && isLandscape && state.orientation != PlayerOrientationRequest.Portrait))

    fun toggle(
        state: PlayerWindowState,
        isTelevision: Boolean,
        isCompactDevice: Boolean,
        isLandscape: Boolean,
    ): PlayerWindowState = when {
        isTelevision -> state
        isImmersive(state, isTelevision, isCompactDevice, isLandscape) -> PlayerWindowState(
            userFullscreen = false,
            orientation = if (isLandscape) PlayerOrientationRequest.Portrait else PlayerOrientationRequest.FollowDevice,
        )
        else -> PlayerWindowState(
            userFullscreen = true,
            orientation = PlayerOrientationRequest.Landscape,
        )
    }
}

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

    /** Leave the player: save progress, release Media3, pop the route once. */
    ExitPlayer,
}

object PlayerBackPolicy {
    /** The top-left button and the Android system back share this decision. */
    fun onBack(panel: PlayerPanel): PlayerBackAction = when (panel) {
        PlayerPanel.None -> PlayerBackAction.ExitPlayer
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

package fr.zyviotv.player.ui.player

import fr.zyviotv.player.data.network.NetworkAvailability
import fr.zyviotv.player.shared.playback.PlaybackRequest
import fr.zyviotv.player.shared.playback.PlaybackValidationResult
import fr.zyviotv.player.shared.playback.PlaybackValidator

enum class PlaybackBlockReason {
    MissingSource,
    InvalidSource,
    NoNetwork,
}

sealed interface PlaybackLaunchDecision {
    data class Launch(val request: PlaybackRequest) : PlaybackLaunchDecision

    data class Blocked(
        val reason: PlaybackBlockReason,
        val message: String,
    ) : PlaybackLaunchDecision
}

/**
 * Single gate for every "play" action (Live, VOD, episodes, zapping, resume).
 *
 * Only a missing/invalid source or a confirmed absence of device network can
 * refuse playback, and a refusal always carries a user-facing message: no
 * silent button. Catalog origin (disk cache vs. network) and Supabase/EPG
 * availability are intentionally not inputs (bloc #207).
 */
object PlaybackLaunchPolicy {
    fun decide(
        request: PlaybackRequest,
        network: NetworkAvailability,
    ): PlaybackLaunchDecision {
        if (request.streamUrl.isBlank()) {
            return PlaybackLaunchDecision.Blocked(
                reason = PlaybackBlockReason.MissingSource,
                message = MISSING_SOURCE_MESSAGE,
            )
        }

        val validation = PlaybackValidator.validate(request)
        if (validation is PlaybackValidationResult.Invalid) {
            return PlaybackLaunchDecision.Blocked(
                reason = PlaybackBlockReason.InvalidSource,
                message = validation.message,
            )
        }

        if (!network.allowsNetworkActions) {
            return PlaybackLaunchDecision.Blocked(
                reason = PlaybackBlockReason.NoNetwork,
                message = NO_NETWORK_MESSAGE,
            )
        }

        return PlaybackLaunchDecision.Launch(request.copy(streamUrl = request.streamUrl.trim()))
    }

    const val MISSING_SOURCE_MESSAGE =
        "Source de lecture indisponible pour ce contenu. Actualisez la playlist puis réessayez."
    const val NO_NETWORK_MESSAGE =
        "Aucune connexion Internet détectée sur l’appareil. Reconnectez-vous puis réessayez."
}

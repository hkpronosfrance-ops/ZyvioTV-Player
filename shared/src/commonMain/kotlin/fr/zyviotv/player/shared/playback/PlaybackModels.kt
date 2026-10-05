package fr.zyviotv.player.shared.playback

enum class PlaybackKind {
    Live,
    Movie,
    Episode,
}

data class PlaybackRequest(
    val title: String,
    val streamUrl: String,
    val kind: PlaybackKind,
    val resumePositionMs: Long = 0L,
)

sealed interface PlaybackValidationResult {
    data object Valid : PlaybackValidationResult
    data class Invalid(val message: String) : PlaybackValidationResult
}

object PlaybackValidator {
    fun validate(request: PlaybackRequest): PlaybackValidationResult {
        val url = request.streamUrl.trim()

        if (url.isBlank()) {
            return PlaybackValidationResult.Invalid("Le flux vidéo est introuvable.")
        }

        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return PlaybackValidationResult.Invalid("Le protocole du flux vidéo n’est pas pris en charge.")
        }

        if (request.resumePositionMs < 0L) {
            return PlaybackValidationResult.Invalid("La position de reprise est invalide.")
        }

        return PlaybackValidationResult.Valid
    }
}

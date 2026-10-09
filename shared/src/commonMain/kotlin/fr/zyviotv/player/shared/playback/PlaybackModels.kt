package fr.zyviotv.player.shared.playback

import fr.zyviotv.player.shared.security.SecretLoggingPolicy

enum class PlaybackKind {
    Live,
    Movie,
    Episode,
}

enum class PlaybackMediaType {
    Hls,
    TransportStream,
    Progressive,
    Unknown,
}

enum class PlaybackState {
    Idle,
    Buffering,
    Ready,
    Ended,
    Error,
}

object PlaybackMediaTypeResolver {
    fun resolve(request: PlaybackRequest): PlaybackMediaType {
        val lowerUrl = request.streamUrl.substringBefore('#').lowercase()
        val cleanUrl = lowerUrl.substringBefore('?')
        val query = lowerUrl.substringAfter('?', "")

        return when {
            cleanUrl.endsWith(".m3u8") -> PlaybackMediaType.Hls
            // get.php / panel style URLs announce HLS through a query value
            // such as output=m3u8 or type=hls instead of a file extension.
            HLS_QUERY_HINT.containsMatchIn(query) -> PlaybackMediaType.Hls
            cleanUrl.endsWith(".ts") -> PlaybackMediaType.TransportStream
            cleanUrl.endsWith(".mp4") ||
                cleanUrl.endsWith(".mkv") ||
                cleanUrl.endsWith(".webm") -> PlaybackMediaType.Progressive
            request.kind == PlaybackKind.Live -> PlaybackMediaType.TransportStream
            else -> PlaybackMediaType.Unknown
        }
    }

    /**
     * Ordered container attempts for a request. IPTV providers frequently
     * serve an HLS playlist behind an extensionless or `.ts` URL, so every
     * guess that is not explicitly HLS or progressive gets one HLS fallback
     * when the first container cannot be parsed.
     */
    fun attempts(request: PlaybackRequest): List<PlaybackMediaType> =
        when (val primary = resolve(request)) {
            PlaybackMediaType.Hls,
            PlaybackMediaType.Progressive,
            -> listOf(primary)
            PlaybackMediaType.TransportStream,
            PlaybackMediaType.Unknown,
            -> listOf(primary, PlaybackMediaType.Hls)
        }

    private val HLS_QUERY_HINT = Regex("(^|&)[a-z_]+=(m3u8|hls)(&|$)")
}

data class PlaybackRequest(
    val title: String,
    val streamUrl: String,
    val kind: PlaybackKind,
    val resumePositionMs: Long = 0L,
) {
    override fun toString(): String =
        "PlaybackRequest(title=$title, streamUrl=${SecretLoggingPolicy.redactUrlForLogs(streamUrl)}, kind=$kind, resumePositionMs=$resumePositionMs)"
}

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

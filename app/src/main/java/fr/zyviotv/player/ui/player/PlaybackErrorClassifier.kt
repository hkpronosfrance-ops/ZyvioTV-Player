package fr.zyviotv.player.ui.player

import android.util.Log
import androidx.media3.common.PlaybackException
import fr.zyviotv.player.data.network.NetworkDiagnostics
import fr.zyviotv.player.shared.playback.PlaybackMediaType
import fr.zyviotv.player.shared.playback.PlaybackRequest
import fr.zyviotv.player.shared.playback.PlaybackSource

enum class PlaybackErrorKind(val logName: String) {
    Network("network"),
    Timeout("timeout"),
    AccessDenied("http-denied"),
    NotFound("http-not-found"),
    HttpStatus("http-status"),
    Cleartext("cleartext-blocked"),
    UnsupportedContainer("container"),
    Decoder("decoder"),
    BehindLiveWindow("behind-live-window"),
    Other("other"),
}

/**
 * Which decoder failed, without any stream data (phase #209). Used to tell an
 * emulator limitation (goldfish/ranchu codecs, HEVC) from an application or
 * stream error, in Logcat and in the message shown to the user.
 */
data class DecoderDiagnosis(
    val mimeType: String?,
    val codecName: String?,
    val isEmulator: Boolean,
) {
    val isHevc: Boolean
        get() = mimeType.equals(HEVC_MIME, ignoreCase = true) ||
            codecName?.contains("hevc", ignoreCase = true) == true

    /** Emulator codecs (c2.goldfish.*) or HEVC on an emulator: not an app defect by itself. */
    val isLikelyEmulatorLimitation: Boolean
        get() = isEmulator && (isHevc || codecName?.contains("goldfish", ignoreCase = true) == true)

    fun logFields(): String =
        "mime=${mimeType ?: "unknown"} codec=${codecName ?: "unknown"} emulator=$isEmulator"

    companion object {
        const val HEVC_MIME = "video/hevc"

        fun isEmulator(fingerprint: String, hardware: String, product: String): Boolean =
            fingerprint.startsWith("generic") ||
                fingerprint.contains("emulator", ignoreCase = true) ||
                hardware in EMULATOR_HARDWARE ||
                product.contains("sdk_gphone", ignoreCase = true) ||
                product.startsWith("sdk") ||
                product.contains("emulator", ignoreCase = true)

        private val EMULATOR_HARDWARE = setOf("goldfish", "ranchu")
    }
}

data class PlaybackFailure(
    val kind: PlaybackErrorKind,
    val httpStatus: Int? = null,
    val decoder: DecoderDiagnosis? = null,
) {
    /** A different container (HLS) may succeed where sniffing failed. */
    val canTryNextContainer: Boolean
        get() = kind == PlaybackErrorKind.UnsupportedContainer

    /**
     * One silent retry is worth it for transport problems and generic
     * provider errors (5xx), never for refused access, missing streams,
     * blocked cleartext or unsupported codecs.
     */
    val isTransient: Boolean
        get() = kind == PlaybackErrorKind.Network ||
            kind == PlaybackErrorKind.Timeout ||
            kind == PlaybackErrorKind.HttpStatus ||
            kind == PlaybackErrorKind.Other

    /** French, credential-free message shown in the player error state. */
    val userMessage: String
        get() = when (kind) {
            PlaybackErrorKind.Network ->
                "Connexion au flux impossible. Vérifiez votre réseau puis réessayez."
            PlaybackErrorKind.Timeout ->
                "Le fournisseur met trop de temps à répondre. Réessayez ou choisissez un autre contenu."
            PlaybackErrorKind.AccessDenied ->
                "Le fournisseur refuse l’accès à ce flux (HTTP ${httpStatus ?: 403}). " +
                    "Vérifiez votre abonnement ou le nombre de connexions autorisées."
            PlaybackErrorKind.NotFound ->
                "Ce flux n’existe plus chez le fournisseur (HTTP ${httpStatus ?: 404}). Actualisez la playlist."
            PlaybackErrorKind.HttpStatus ->
                "Le fournisseur a répondu par une erreur" +
                    (httpStatus?.let { " (HTTP $it)" } ?: "") + ". Réessayez plus tard."
            PlaybackErrorKind.Cleartext ->
                "La politique réseau de l’appareil bloque ce flux HTTP."
            PlaybackErrorKind.UnsupportedContainer ->
                "Format de flux non reconnu par le lecteur Android."
            PlaybackErrorKind.Decoder -> when {
                decoder?.isLikelyEmulatorLimitation == true ->
                    "Le décodeur vidéo de l’émulateur ne parvient pas à lire ce flux" +
                        (if (decoder.isHevc) " (HEVC/H.265)" else "") +
                        ". Testez sur un appareil réel."
                decoder?.isHevc == true ->
                    "Cet appareil ne parvient pas à décoder ce flux HEVC (H.265)."
                else -> "Le codec de ce flux n’est pas pris en charge par cet appareil."
            }
            PlaybackErrorKind.BehindLiveWindow,
            PlaybackErrorKind.Other,
            -> "Impossible de lire ce flux. Réessayez ou choisissez un autre contenu."
        }
}

object PlaybackErrorClassifier {
    fun classify(
        errorCode: Int,
        httpStatus: Int?,
        decoder: DecoderDiagnosis? = null,
    ): PlaybackFailure {
        val kind = when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            -> PlaybackErrorKind.Network

            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT,
            -> PlaybackErrorKind.Timeout

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (httpStatus) {
                401, 403, 407, 451, 456, 458 -> PlaybackErrorKind.AccessDenied
                404, 410 -> PlaybackErrorKind.NotFound
                else -> PlaybackErrorKind.HttpStatus
            }

            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackErrorKind.NotFound
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlaybackErrorKind.Cleartext

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            -> PlaybackErrorKind.UnsupportedContainer

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            -> PlaybackErrorKind.Decoder

            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PlaybackErrorKind.BehindLiveWindow

            else -> PlaybackErrorKind.Other
        }
        return PlaybackFailure(
            kind = kind,
            httpStatus = httpStatus,
            decoder = decoder.takeIf { kind == PlaybackErrorKind.Decoder },
        )
    }
}

/** Logcat filter: `tag:ZyvioPlayback`. Never logs URLs, titles or credentials. */
internal object PlaybackDiagnostics {
    private const val TAG = "ZyvioPlayback"

    fun launch(request: PlaybackRequest) {
        val source = PlaybackSource.parse(request.streamUrl)
        Log.i(
            TAG,
            "launch kind=${request.kind.name.lowercase()} " +
                "transport=${NetworkDiagnostics.safeEndpoint(source.url)} " +
                "provider_headers=${source.headers.size} " +
                "resume=${request.resumePositionMs > 0L}",
        )
    }

    fun blocked(reason: PlaybackBlockReason) {
        Log.w(TAG, "blocked reason=${reason.name.lowercase()}")
    }

    fun attempt(mediaType: PlaybackMediaType, index: Int) {
        Log.i(TAG, "prepare container=${mediaType.name.lowercase()} attempt=${index + 1}")
    }

    fun released(reason: String) {
        Log.i(TAG, "release reason=$reason")
    }

    fun state(name: String) {
        Log.i(TAG, "state=$name")
    }

    fun failure(failure: PlaybackFailure, errorCodeName: String, terminal: Boolean) {
        Log.w(
            TAG,
            "failure kind=${failure.kind.logName} code=$errorCodeName " +
                "http=${failure.httpStatus ?: "none"} terminal=$terminal" +
                (failure.decoder?.let { " " + it.logFields() } ?: ""),
        )
    }
}

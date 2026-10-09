package fr.zyviotv.player.ui.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorClassifierTest {
    @Test
    fun forbiddenProviderResponseIsAccessDeniedWithoutRetry() {
        val failure = PlaybackErrorClassifier.classify(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            httpStatus = 403,
        )

        assertEquals(PlaybackErrorKind.AccessDenied, failure.kind)
        assertFalse(failure.isTransient)
        assertTrue(failure.userMessage.contains("403"))
    }

    @Test
    fun missingStreamIsNotFound() {
        val failure = PlaybackErrorClassifier.classify(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            httpStatus = 404,
        )

        assertEquals(PlaybackErrorKind.NotFound, failure.kind)
    }

    @Test
    fun providerServerErrorGetsOneSilentRetry() {
        val failure = PlaybackErrorClassifier.classify(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            httpStatus = 503,
        )

        assertEquals(PlaybackErrorKind.HttpStatus, failure.kind)
        assertTrue(failure.isTransient)
    }

    @Test
    fun unrecognisedContainerTriesNextContainer() {
        listOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        ).forEach { code ->
            val failure = PlaybackErrorClassifier.classify(code, httpStatus = null)
            assertTrue(failure.canTryNextContainer)
            assertFalse(failure.isTransient)
        }
    }

    @Test
    fun networkAndTimeoutAreTransient() {
        assertTrue(
            PlaybackErrorClassifier.classify(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                null,
            ).isTransient,
        )
        assertEquals(
            PlaybackErrorKind.Timeout,
            PlaybackErrorClassifier.classify(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                null,
            ).kind,
        )
    }

    @Test
    fun decoderAndCleartextFailuresAreTerminal() {
        val decoder = PlaybackErrorClassifier.classify(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            null,
        )
        val cleartext = PlaybackErrorClassifier.classify(
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            null,
        )

        assertEquals(PlaybackErrorKind.Decoder, decoder.kind)
        assertFalse(decoder.isTransient || decoder.canTryNextContainer)
        assertEquals(PlaybackErrorKind.Cleartext, cleartext.kind)
        assertFalse(cleartext.isTransient)
    }

    @Test
    fun behindLiveWindowIsRecognised() {
        assertEquals(
            PlaybackErrorKind.BehindLiveWindow,
            PlaybackErrorClassifier.classify(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW, null).kind,
        )
    }

    @Test
    fun everyKindHasAFrenchMessage() {
        PlaybackErrorKind.entries.forEach { kind ->
            assertTrue(kind.name, PlaybackFailure(kind).userMessage.isNotBlank())
        }
    }
}

package fr.zyviotv.player.data.network

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NetworkDiagnosticsTest {
    @Test
    fun safeEndpointRemovesEverySecretBearingUrlPart() {
        val original =
            "http://alice:super-secret@provider.example:8080/live/alice/super-secret/42.ts" +
                "?username=alice&password=super-secret&token=private" // [REDACTED] fixture

        val diagnostic = NetworkDiagnostics.safeEndpoint(original)

        assertEquals("http", diagnostic)
        assertFalse(diagnostic.contains("provider.example"))
        assertFalse(diagnostic.contains("alice"))
        assertFalse(diagnostic.contains("super-secret"))
        assertFalse(diagnostic.contains("private"))
        assertFalse(diagnostic.contains("42.ts"))
    }

    @Test
    fun safeEndpointOmitsDefaultPortAndRejectsInvalidUrls() {
        assertEquals(
            "https",
            NetworkDiagnostics.safeEndpoint("https://iptv.example:443/list.m3u"),
        )
        assertEquals("invalid-url", NetworkDiagnostics.safeEndpoint("not a url password=secret"))
    }

    @Test
    fun supabaseErrorsAreClassifiedWithoutReturningTheResponse() {
        assertEquals("invalid-jwt", NetworkDiagnostics.supabaseErrorKind("{\"message\":\"Invalid JWT\"}"))
        assertEquals("expired-jwt", NetworkDiagnostics.supabaseErrorKind("{\"message\":\"JWT expired\"}"))
        assertEquals("missing-credentials", NetworkDiagnostics.supabaseErrorKind("MISSING_CREDENTIALS"))
        assertEquals("permission-denied", NetworkDiagnostics.supabaseErrorKind("permission denied for table"))
        assertEquals("unclassified", NetworkDiagnostics.supabaseErrorKind("private-token-value"))
    }

    @Test
    fun failuresAreClassifiedWithoutUsingExceptionMessages() {
        assertEquals("timeout", NetworkDiagnostics.failureKind(SocketTimeoutException("secret-url")))
        assertEquals("dns", NetworkDiagnostics.failureKind(UnknownHostException("secret-host")))
        assertEquals("connection", NetworkDiagnostics.failureKind(ConnectException("secret-url")))
        assertEquals("tls", NetworkDiagnostics.failureKind(SSLException("secret-url")))
        assertEquals("security-policy", NetworkDiagnostics.failureKind(SecurityException("secret-url")))
    }
}

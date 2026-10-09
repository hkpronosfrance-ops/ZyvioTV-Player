package fr.zyviotv.player.data.network

import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import fr.zyviotv.player.ui.player.DiagnosticsSafety
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
        assertEquals("upstream-timeout", NetworkDiagnostics.supabaseErrorKind("upstream timed out"))
        assertEquals("unclassified", NetworkDiagnostics.supabaseErrorKind("private-token-value"))
    }

    @Test
    fun failuresAreClassifiedWithoutUsingExceptionMessages() {
        assertEquals("timeout", NetworkDiagnostics.failureKind(SocketTimeoutException("secret-url")))
        assertEquals("dns", NetworkDiagnostics.failureKind(UnknownHostException("secret-host")))
        assertEquals("connection", NetworkDiagnostics.failureKind(ConnectException("secret-url")))
        assertEquals("truncated", NetworkDiagnostics.failureKind(EOFException("secret-url")))
        assertEquals("connection-interrupted", NetworkDiagnostics.failureKind(SocketException("secret-url")))
        assertEquals("tls", NetworkDiagnostics.failureKind(SSLException("secret-url")))
        assertEquals("security-policy", NetworkDiagnostics.failureKind(SecurityException("secret-url")))
    }

    @Test
    fun postgresPermissionErrorsAreSplitBetweenGrantAndRowLevelSecurity() {
        val missingGrant = "{\"code\":\"42501\",\"details\":null,\"hint\":null," +
            "\"message\":\"permission denied for table player_devices\"}"
        val rlsRefusal = "{\"code\":\"42501\",\"message\":\"new row violates row-level security policy for table \\\"player_devices\\\"\"}"
        assertEquals("42501", NetworkDiagnostics.supabaseErrorCode(missingGrant))
        assertEquals("missing-table-grant", NetworkDiagnostics.postgresErrorCategory(missingGrant))
        assertEquals("rls-violation", NetworkDiagnostics.postgresErrorCategory(rlsRefusal))
        assertEquals("missing-function-grant", NetworkDiagnostics.postgresErrorCategory("permission denied for function x"))
        assertEquals("PGRST301", NetworkDiagnostics.supabaseErrorCode("{\"code\":\"PGRST301\"}"))
        assertEquals("none", NetworkDiagnostics.supabaseErrorCode("<html>Forbidden</html>"))
        assertEquals("other", NetworkDiagnostics.supabaseErrorCode("{\"code\":\"eyJhbGciOi\"}"))
        assertEquals("none", NetworkDiagnostics.postgresErrorCategory("{}"))
    }

    @Test
    fun supabaseLineNeverCarriesTheBodyOrSecrets() {
        val hostile = "{\"code\":\"42501\",\"message\":\"permission denied for table player_devices\"," +
            "\"hint\":\"Authorization: Bearer eyJ.token https://x.supabase.co/rest/v1/player_devices?username=a&password=b\"}"
        val line = NetworkDiagnostics.supabaseLine("player_devices", 403, "fresh", hostile, retried = false)
        assertEquals(
            "supabase operation=player_devices response=403 session=fresh authError=permission-denied " +
                "sqlstate=42501 pg=missing-table-grant retried=false",
            line,
        )
        DiagnosticsSafety.assertSafe(line)
        DiagnosticsSafety.assertSafe(
            NetworkDiagnostics.supabaseLine("https://evil?token=1", 401, "Bearer x", "missing authorization header", true),
        )
        assertEquals("missing-auth-header", NetworkDiagnostics.supabaseErrorKind("Missing authorization header"))
    }
}

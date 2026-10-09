package fr.zyviotv.player.ui.player

import org.junit.Assert.assertFalse

/**
 * Bloc #211: shared assertion for every diagnostic line produced by tests.
 * A log line must never carry a URL, an Xtream query, a password, a token or
 * an Authorization header, whatever the input was.
 */
object DiagnosticsSafety {
    private val FORBIDDEN = listOf("://", "?username=", "password", "token", "authorization")

    fun assertSafe(line: String) {
        val lower = line.lowercase()
        FORBIDDEN.forEach { fragment ->
            assertFalse("Diagnostic line contains \"$fragment\": $line", lower.contains(fragment))
        }
    }
}

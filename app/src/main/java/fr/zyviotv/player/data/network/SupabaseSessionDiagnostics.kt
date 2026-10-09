package fr.zyviotv.player.data.network

internal object SupabaseSessionDiagnostics {
    private const val EXPIRY_SAFETY_SECONDS = 60L

    fun state(
        accessToken: String?,
        expiresAtEpochSeconds: Long?,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
    ): String = when {
        accessToken == null || expiresAtEpochSeconds == null -> "missing"
        accessToken.count { it == '.' } != 2 -> "non-jwt"
        expiresAtEpochSeconds <= nowEpochSeconds -> "expired"
        expiresAtEpochSeconds <= nowEpochSeconds + EXPIRY_SAFETY_SECONDS -> "expiring"
        else -> "fresh"
    }

    fun operation(path: String): String =
        path.substringBefore('?').substringAfterLast('/').ifBlank { "unknown" }

    fun shouldRefreshBeforeRequest(state: String): Boolean =
        state == "expired" || state == "expiring"

    fun shouldRetryUnauthorized(statusCode: Int, alreadyRefreshed: Boolean): Boolean =
        statusCode == 401 && !alreadyRefreshed
}

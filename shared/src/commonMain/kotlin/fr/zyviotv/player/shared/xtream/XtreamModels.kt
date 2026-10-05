package fr.zyviotv.player.shared.xtream

data class XtreamCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
)

data class XtreamServerInfo(
    val url: String,
    val port: Int?,
    val httpsPort: Int?,
    val serverProtocol: String?,
    val timezone: String?,
)

data class XtreamAccountInfo(
    val username: String,
    val status: String,
    val isTrial: Boolean,
    val activeConnections: Int?,
    val maxConnections: Int?,
    val createdAtEpochSeconds: Long?,
    val expiresAtEpochSeconds: Long?,
)

data class XtreamProfile(
    val account: XtreamAccountInfo,
    val server: XtreamServerInfo,
)

sealed interface XtreamValidationResult {
    data object Valid : XtreamValidationResult
    data class Invalid(val message: String) : XtreamValidationResult
}

sealed interface XtreamConnectionResult {
    data class Success(val profile: XtreamProfile) : XtreamConnectionResult
    data class Failure(val message: String) : XtreamConnectionResult
}

interface XtreamClient {
    suspend fun authenticate(credentials: XtreamCredentials): XtreamConnectionResult
}

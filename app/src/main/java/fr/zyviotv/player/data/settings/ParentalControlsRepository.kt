package fr.zyviotv.player.data.settings

import fr.zyviotv.player.BuildConfig
import fr.zyviotv.player.data.auth.SecureSessionStore
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ParentalSettings(
    val hasPin: Boolean,
    val enabled: Boolean,
    val blockedUntil: String?,
)

data class ParentalRuntimeState(
    val serverNowEpochMillis: Long?,
    val parentalEnabled: Boolean,
    val isChild: Boolean,
    val consumedSeconds: Int,
    val limitMinutes: Int?,
    val warningMinutes: Int,
    val scheduleEnabled: Boolean,
    val scheduleWindowsJson: String,
    val exceptionUntil: String?,
    val exceptionUntilEpochMillis: Long?,
    val blockedByTime: Boolean,
)

data class ScreenTimeHeartbeat(
    val consumedSeconds: Int,
    val limitMinutes: Int?,
    val warningMinutes: Int,
    val blockedByTime: Boolean,
)

sealed interface ParentalExceptionResult {
    data class Granted(val expiresAt: String?) : ParentalExceptionResult
    data class Failure(val message: String) : ParentalExceptionResult
}

data class ProfileContentLocks(
    val parentalEnabled: Boolean,
    val isChild: Boolean,
    val hideLocked: Boolean,
    val lockedCategoryKeys: Set<String>,
    val lockedContentKeys: Set<String>,
)

data class ProfileParentalSettings(
    val profileId: String,
    val profileName: String,
    val profileType: String,
    val isPrimary: Boolean,
    val maxAge: Int?,
    val hideLocked: Boolean,
    val dailyLimitMinutes: Int?,
    val weekendLimitMinutes: Int?,
    val warningMinutes: Int,
    val scheduleEnabled: Boolean,
    val scheduleWindowsJson: String,
)

sealed interface PinVerificationResult {
    data object Verified : PinVerificationResult
    data class Invalid(val attemptsRemaining: Int?) : PinVerificationResult
    data class Blocked(val blockedUntil: String?) : PinVerificationResult
    data object NotConfigured : PinVerificationResult
    data class Failure(val message: String) : PinVerificationResult
}

sealed interface ParentalWriteResult {
    data object Success : ParentalWriteResult
    data class Failure(val message: String) : ParentalWriteResult
}

sealed interface ParentalRecoveryResult {
    data class Sent(val maskedEmail: String) : ParentalRecoveryResult
    data class Failure(val message: String) : ParentalRecoveryResult
}

class ParentalControlsRepository(
    private val sessionStore: SecureSessionStore,
) {
    suspend fun requestPinRecoveryEmail(): ParentalRecoveryResult =
        withContext(Dispatchers.IO) {
            val session = sessionStore.load()
                ?: return@withContext ParentalRecoveryResult.Failure(
                    "Session expirée. Reconnectez-vous.",
                )

            val userResponse = authRequest(
                path = "/auth/v1/user",
                method = "GET",
                body = null,
                bearerToken = session.accessToken,
            )
            if (userResponse.code !in 200..299) {
                return@withContext ParentalRecoveryResult.Failure(
                    "Impossible de récupérer l’adresse email du compte.",
                )
            }

            val email = runCatching {
                JSONObject(userResponse.body).optString("email").trim()
            }.getOrDefault("")
            if (email.isBlank()) {
                return@withContext ParentalRecoveryResult.Failure(
                    "Aucune adresse email n’est associée au compte.",
                )
            }

            val otpResponse = authRequest(
                path = "/auth/v1/otp",
                method = "POST",
                body = JSONObject()
                    .put("email", email)
                    .put("create_user", false)
                    .toString(),
                bearerToken = null,
            )

            if (otpResponse.code !in 200..299) {
                return@withContext ParentalRecoveryResult.Failure(
                    "Impossible d’envoyer l’email de récupération.",
                )
            }

            ParentalRecoveryResult.Sent(maskEmail(email))
        }

    suspend fun resetPinAfterRecentAuth(
        newPin: String,
    ): ParentalWriteResult = withContext(Dispatchers.IO) {
        val response = rpc(
            "player_reset_parental_pin_after_recent_auth",
            JSONObject().put("p_new_pin", newPin),
        )
        if (response.code !in 200..299) {
            return@withContext ParentalWriteResult.Failure(
                "Impossible de réinitialiser le code PIN.",
            )
        }

        val json = JSONObject(response.body)
        if (json.optBoolean("success", false)) {
            ParentalWriteResult.Success
        } else {
            val message = when (json.optString("reason")) {
                "invalid_format" -> "Le code PIN doit contenir exactement 4 chiffres."
                "reauth_required" -> "Ouvrez d’abord le lien reçu sur l’email du compte."
                else -> "Impossible de réinitialiser le code PIN."
            }
            ParentalWriteResult.Failure(message)
        }
    }

    suspend fun loadSettings(): Result<ParentalSettings> = withContext(Dispatchers.IO) {
        runCatching {
            val response = rpc("player_get_parental_settings", JSONObject())
            if (response.code !in 200..299) error("Impossible de charger le contrôle parental.")
            val json = JSONObject(response.body)
            ParentalSettings(
                hasPin = json.optBoolean("has_pin", false),
                enabled = json.optBoolean("enabled", false),
                blockedUntil = json.optString("blocked_until").takeIf {
                    it.isNotBlank() && it != "null"
                },
            )
        }
    }

    suspend fun verifyPin(pin: String): PinVerificationResult = withContext(Dispatchers.IO) {
        val response = rpc(
            "player_verify_parental_pin",
            JSONObject().put("p_pin", pin),
        )
        if (response.code !in 200..299) {
            return@withContext PinVerificationResult.Failure(
                "Impossible de vérifier le code PIN.",
            )
        }

        val json = JSONObject(response.body)
        if (json.optBoolean("verified", false)) {
            return@withContext PinVerificationResult.Verified
        }

        when (json.optString("reason")) {
            "invalid" -> PinVerificationResult.Invalid(
                attemptsRemaining = if (json.has("attempts_remaining")) {
                    json.optInt("attempts_remaining")
                } else {
                    null
                },
            )

            "blocked" -> PinVerificationResult.Blocked(
                blockedUntil = json.optString("blocked_until").takeIf { it.isNotBlank() },
            )

            "pin_not_configured" -> PinVerificationResult.NotConfigured
            else -> PinVerificationResult.Failure("Code PIN non vérifié.")
        }
    }

    suspend fun setPin(
        newPin: String,
        currentPin: String?,
    ): ParentalWriteResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_new_pin", newPin)
            .put("p_current_pin", currentPin ?: JSONObject.NULL)
        val response = rpc("player_set_parental_pin", body)
        if (response.code !in 200..299) {
            return@withContext ParentalWriteResult.Failure(
                "Impossible d’enregistrer le code PIN.",
            )
        }

        val json = JSONObject(response.body)
        if (json.optBoolean("success", false)) {
            ParentalWriteResult.Success
        } else {
            val message = when (json.optString("reason")) {
                "invalid_format" -> "Le code PIN doit contenir exactement 4 chiffres."
                "current_pin_invalid" -> "Le code PIN actuel est incorrect."
                "blocked" -> "Trop de tentatives. Réessayez dans quelques minutes."
                else -> "Impossible d’enregistrer le code PIN."
            }
            ParentalWriteResult.Failure(message)
        }
    }

    suspend fun loadContentLocks(
        profileId: String,
    ): Result<ProfileContentLocks> = withContext(Dispatchers.IO) {
        runCatching {
            val response = rpc(
                "player_get_profile_content_locks",
                JSONObject().put("p_profile_id", profileId),
            )
            if (response.code !in 200..299) {
                error("Impossible de charger les verrouillages parentaux.")
            }
            val json = JSONObject(response.body)
            ProfileContentLocks(
                parentalEnabled = json.optBoolean("parental_enabled", false),
                isChild = json.optBoolean("is_child", false),
                hideLocked = json.optBoolean("hide_locked", false),
                lockedCategoryKeys = json.optJSONArray("locked_category_keys").toStringSet(),
                lockedContentKeys = json.optJSONArray("locked_content_keys").toStringSet(),
            )
        }
    }

    suspend fun updateContentLocks(
        profileId: String,
        pin: String,
        lockedCategoryKeys: Set<String>,
        lockedContentKeys: Set<String>,
    ): ParentalWriteResult = withContext(Dispatchers.IO) {
        val response = rpc(
            "player_update_profile_content_locks",
            JSONObject()
                .put("p_profile_id", profileId)
                .put("p_pin", pin)
                .put("p_locked_category_keys", org.json.JSONArray(lockedCategoryKeys.sorted()))
                .put("p_locked_content_keys", org.json.JSONArray(lockedContentKeys.sorted())),
        )
        if (response.code !in 200..299) {
            return@withContext ParentalWriteResult.Failure(
                "Impossible d’enregistrer les verrouillages.",
            )
        }
        val json = JSONObject(response.body)
        if (json.optBoolean("success", false)) {
            ParentalWriteResult.Success
        } else {
            val message = when (json.optString("reason")) {
                "pin_invalid" -> "Code PIN incorrect."
                "pin_not_configured" -> "Configurez d’abord un code PIN."
                "blocked" -> "Trop de tentatives. Réessayez dans quelques minutes."
                "standard_profile" -> "Les profils Standard n’utilisent pas de verrouillages parentaux."
                "invalid_categories" -> "Liste de catégories verrouillées invalide."
                "invalid_content" -> "Liste de contenus verrouillés invalide."
                else -> "Impossible d’enregistrer les verrouillages."
            }
            ParentalWriteResult.Failure(message)
        }
    }

    suspend fun loadProfileSettings(
        profileId: String,
    ): Result<ProfileParentalSettings> = withContext(Dispatchers.IO) {
        runCatching {
            val response = rpc(
                "player_get_profile_parental_settings",
                JSONObject().put("p_profile_id", profileId),
            )
            if (response.code !in 200..299) error("Impossible de charger les restrictions.")
            val json = JSONObject(response.body)
            ProfileParentalSettings(
                profileId = json.getString("profile_id"),
                profileName = json.getString("profile_name"),
                profileType = json.optString("profile_type", "standard"),
                isPrimary = json.optBoolean("is_primary", false),
                maxAge = if (json.isNull("max_age")) null else json.getInt("max_age"),
                hideLocked = json.optBoolean("hide_locked", false),
                dailyLimitMinutes = if (json.isNull("daily_limit_minutes")) null else json.getInt("daily_limit_minutes"),
                weekendLimitMinutes = if (json.isNull("weekend_limit_minutes")) null else json.getInt("weekend_limit_minutes"),
                warningMinutes = json.optInt("warning_minutes", 10),
                scheduleEnabled = json.optBoolean("schedule_enabled", false),
                scheduleWindowsJson = json.optJSONArray("schedule_windows")?.toString() ?: "[]",
            )
        }
    }

    suspend fun updateProfileSettings(
        profileId: String,
        pin: String,
        maxAge: Int?,
        hideLocked: Boolean,
        dailyLimitMinutes: Int?,
        weekendLimitMinutes: Int?,
        warningMinutes: Int,
        scheduleEnabled: Boolean,
        scheduleWindowsJson: String = "[]",
    ): ParentalWriteResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_profile_id", profileId)
            .put("p_pin", pin)
            .put("p_max_age", maxAge ?: JSONObject.NULL)
            .put("p_hide_locked", hideLocked)
            .put("p_daily_limit_minutes", dailyLimitMinutes ?: JSONObject.NULL)
            .put("p_weekend_limit_minutes", weekendLimitMinutes ?: JSONObject.NULL)
            .put("p_warning_minutes", warningMinutes)
            .put("p_schedule_enabled", scheduleEnabled)
            .put("p_schedule_windows", org.json.JSONArray(scheduleWindowsJson))

        val response = rpc("player_update_profile_parental_settings", body)
        if (response.code !in 200..299) {
            return@withContext ParentalWriteResult.Failure(
                "Impossible d’enregistrer les restrictions du profil.",
            )
        }
        val json = JSONObject(response.body)
        if (json.optBoolean("success", false)) {
            ParentalWriteResult.Success
        } else {
            val message = when (json.optString("reason")) {
                "pin_invalid" -> "Code PIN incorrect."
                "pin_not_configured" -> "Configurez d’abord un code PIN."
                "blocked" -> "Trop de tentatives. Réessayez dans quelques minutes."
                "invalid_age" -> "Restriction d’âge invalide."
                "invalid_limit" -> "Limite de temps invalide."
                "invalid_warning" -> "Avertissement de fin invalide."
                "invalid_schedule" -> "Plages horaires invalides."
                "primary_unrestricted" -> "Le profil principal reste sans restriction d’âge."
                else -> "Impossible d’enregistrer les restrictions du profil."
            }
            ParentalWriteResult.Failure(message)
        }
    }

    suspend fun loadRuntimeState(
        profileId: String,
        contentKey: String,
    ): Result<ParentalRuntimeState> = withContext(Dispatchers.IO) {
        runCatching {
            val response = rpc(
                "player_parental_runtime_state",
                JSONObject()
                    .put("p_profile_id", profileId)
                    .put("p_content_key", contentKey),
            )
            if (response.code !in 200..299) error("Impossible de charger l’état parental.")
            val json = JSONObject(response.body)
            ParentalRuntimeState(
                serverNowEpochMillis = if (json.isNull("server_now_epoch_ms")) {
                    null
                } else {
                    json.optLong("server_now_epoch_ms")
                },
                parentalEnabled = json.optBoolean("parental_enabled", false),
                isChild = json.optBoolean("is_child", false),
                consumedSeconds = json.optInt("consumed_seconds", 0),
                limitMinutes = if (json.isNull("limit_minutes")) null else json.getInt("limit_minutes"),
                warningMinutes = json.optInt("warning_minutes", 10),
                scheduleEnabled = json.optBoolean("schedule_enabled", false),
                scheduleWindowsJson = json.optJSONArray("schedule_windows")?.toString() ?: "[]",
                exceptionUntil = json.optString("exception_until").takeIf {
                    it.isNotBlank() && it != "null"
                },
                exceptionUntilEpochMillis = if (json.isNull("exception_until_epoch_ms")) {
                    null
                } else {
                    json.optLong("exception_until_epoch_ms")
                },
                blockedByTime = json.optBoolean("blocked_by_time", false),
            )
        }
    }

    suspend fun heartbeatScreenTime(
        profileId: String,
        playing: Boolean,
        contentKey: String,
    ): Result<ScreenTimeHeartbeat> = withContext(Dispatchers.IO) {
        runCatching {
            val response = rpc(
                "player_parental_screen_time_heartbeat",
                JSONObject()
                    .put("p_profile_id", profileId)
                    .put("p_playing", playing)
                    .put("p_content_key", contentKey),
            )
            if (response.code !in 200..299) error("Impossible de synchroniser le temps d’écran.")
            val json = JSONObject(response.body)
            ScreenTimeHeartbeat(
                consumedSeconds = json.optInt("consumed_seconds", 0),
                limitMinutes = if (json.isNull("limit_minutes")) null else json.getInt("limit_minutes"),
                warningMinutes = json.optInt("warning_minutes", 10),
                blockedByTime = json.optBoolean("blocked_by_time", false),
            )
        }
    }

    suspend fun grantRuntimeException(
        profileId: String,
        pin: String,
        contentKey: String,
    ): ParentalExceptionResult = withContext(Dispatchers.IO) {
        val response = rpc(
            "player_parental_grant_exception",
            JSONObject()
                .put("p_profile_id", profileId)
                .put("p_pin", pin)
                .put("p_content_key", contentKey),
        )
        if (response.code !in 200..299) {
            return@withContext ParentalExceptionResult.Failure(
                "Impossible d’autoriser l’exception parentale.",
            )
        }
        val json = JSONObject(response.body)
        if (json.optBoolean("success", false)) {
            ParentalExceptionResult.Granted(
                json.optString("expires_at").takeIf { it.isNotBlank() && it != "null" },
            )
        } else {
            val message = when (json.optString("reason")) {
                "pin_invalid" -> "Code PIN incorrect."
                "pin_not_configured" -> "Aucun code PIN parental n’est configuré."
                "blocked" -> "Trop de tentatives. Réessayez dans quelques minutes."
                "profile_not_found" -> "Profil introuvable."
                else -> "Impossible d’autoriser l’exception parentale."
            }
            ParentalExceptionResult.Failure(message)
        }
    }

    suspend fun endRuntimeException(
        profileId: String,
        contentKey: String,
    ): Unit = withContext(Dispatchers.IO) {
        rpc(
            "player_parental_end_exception",
            JSONObject()
                .put("p_profile_id", profileId)
                .put("p_content_key", contentKey),
        )
        Unit
    }

    suspend fun setEnabled(
        pin: String,
        enabled: Boolean,
    ): ParentalWriteResult = withContext(Dispatchers.IO) {
        val response = rpc(
            "player_set_parental_enabled",
            JSONObject()
                .put("p_pin", pin)
                .put("p_enabled", enabled),
        )
        if (response.code !in 200..299) {
            return@withContext ParentalWriteResult.Failure(
                "Impossible de modifier le contrôle parental.",
            )
        }
        val json = JSONObject(response.body)
        if (json.optBoolean("success", false)) {
            ParentalWriteResult.Success
        } else {
            val message = when (json.optString("reason")) {
                "pin_invalid" -> "Code PIN incorrect."
                "pin_not_configured" -> "Configurez d’abord un code PIN."
                "blocked" -> "Trop de tentatives. Réessayez dans quelques minutes."
                else -> "Impossible de modifier le contrôle parental."
            }
            ParentalWriteResult.Failure(message)
        }
    }

    private fun authRequest(
        path: String,
        method: String,
        body: String?,
        bearerToken: String?,
    ): HttpResponse {
        val connection = (
            URL(BuildConfig.SUPABASE_URL + path)
                .openConnection() as HttpURLConnection
            )
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.doOutput = body != null
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty(
                "Authorization",
                "Bearer " + (bearerToken ?: BuildConfig.SUPABASE_PUBLISHABLE_KEY),
            )
            if (body != null) {
                connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use {
                    it.write(body)
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return HttpResponse(
                code = code,
                body = stream?.bufferedReader()?.use { it.readText() }.orEmpty(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 1) return "***"
        val local = email.substring(0, at)
        val domain = email.substring(at)
        return local.take(1) + "***" + local.takeLast(1) + domain
    }

    private fun rpc(
        name: String,
        body: JSONObject,
    ): HttpResponse {
        val session = sessionStore.load()
            ?: return HttpResponse(401, "")
        val connection = (
            URL(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/" + name)
                .openConnection() as HttpURLConnection
            )

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty(
                "Authorization",
                "Bearer " + session.accessToken,
            )
            connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use {
                it.write(body.toString())
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return HttpResponse(
                code = code,
                body = stream?.bufferedReader()?.use { it.readText() }.orEmpty(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
    )
}


private fun org.json.JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return buildSet {
        for (index in 0 until length()) {
            optString(index)
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let(::add)
        }
    }
}

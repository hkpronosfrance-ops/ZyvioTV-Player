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
    val maxAge: Int?,
    val hideLocked: Boolean,
    val scheduleEnabled: Boolean,
    val dailyLimitMinutes: Int?,
    val blockedUntil: String?,
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

class ParentalControlsRepository(
    private val sessionStore: SecureSessionStore,
) {
    suspend fun loadSettings(): Result<ParentalSettings> = withContext(Dispatchers.IO) {
        runCatching {
            val response = rpc("player_get_parental_settings", JSONObject())
            if (response.code !in 200..299) error("Impossible de charger le contrôle parental.")
            val json = JSONObject(response.body)
            ParentalSettings(
                hasPin = json.optBoolean("has_pin", false),
                maxAge = if (json.isNull("max_age")) null else json.getInt("max_age"),
                hideLocked = json.optBoolean("hide_locked", false),
                scheduleEnabled = json.optBoolean("schedule_enabled", false),
                dailyLimitMinutes = if (json.isNull("daily_limit_minutes")) {
                    null
                } else {
                    json.getInt("daily_limit_minutes")
                },
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

    suspend fun updateSettings(
        pin: String,
        maxAge: Int?,
        hideLocked: Boolean,
        scheduleEnabled: Boolean,
        dailyLimitMinutes: Int?,
    ): ParentalWriteResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_pin", pin)
            .put("p_max_age", maxAge ?: JSONObject.NULL)
            .put("p_hide_locked", hideLocked)
            .put("p_schedule_enabled", scheduleEnabled)
            .put("p_daily_limit_minutes", dailyLimitMinutes ?: JSONObject.NULL)

        val response = rpc("player_update_parental_settings", body)
        if (response.code !in 200..299) {
            return@withContext ParentalWriteResult.Failure(
                "Impossible d’enregistrer le contrôle parental.",
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
                "invalid_limit" -> "Limite quotidienne invalide."
                else -> "Impossible d’enregistrer le contrôle parental."
            }
            ParentalWriteResult.Failure(message)
        }
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

package fr.zyviotv.player.data.system

import fr.zyviotv.player.BuildConfig
import fr.zyviotv.player.data.auth.SecureSessionStore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

sealed interface SystemGateState {
    data object Normal : SystemGateState
    data class PlannedMaintenance(val message: String?) : SystemGateState
    data class BlockingMaintenance(val message: String?) : SystemGateState
    data class AccountSuspended(val message: String?) : SystemGateState
}

class SystemStateRepository(
    private val sessionStore: SecureSessionStore,
) {
    suspend fun loadAndroidState(): SystemGateState = withContext(Dispatchers.IO) {
        val session = sessionStore.load() ?: return@withContext SystemGateState.Normal

        val account = request(
            path = "/rest/v1/player_account_status" +
                "?select=status,message&limit=1",
            accessToken = session.accessToken,
        )
        if (account != null) {
            val row = account.firstOrNull()
            if (row?.optString("status") == "suspended") {
                return@withContext SystemGateState.AccountSuspended(
                    row.optString("message").takeIf { it.isNotBlank() },
                )
            }
        }

        val service = request(
            path = "/rest/v1/player_service_state" +
                "?platform=eq.android" +
                "&select=blocking,maintenance_message&limit=1",
            accessToken = session.accessToken,
        ) ?: return@withContext SystemGateState.Normal

        val row = service.firstOrNull() ?: return@withContext SystemGateState.Normal
        val message = row.optString("maintenance_message").takeIf { it.isNotBlank() }
        if (row.optBoolean("blocking", false)) {
            SystemGateState.BlockingMaintenance(message)
        } else if (message != null) {
            SystemGateState.PlannedMaintenance(message)
        } else {
            SystemGateState.Normal
        }
    }

    private fun request(path: String, accessToken: String): JSONArray? {
        val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")

            if (connection.responseCode !in 200..299) return null
            JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.firstOrNull() =
        if (length() > 0) optJSONObject(0) else null
}

package fr.zyviotv.player.data.settings

import fr.zyviotv.player.BuildConfig
import fr.zyviotv.player.data.auth.SecureSessionStore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

enum class AppUpdateKind {
    None,
    Optional,
    Mandatory,
}

data class AppUpdatePolicy(
    val kind: AppUpdateKind,
    val latestVersionCode: Long,
    val minimumVersionCode: Long,
    val storeUrl: String?,
)

class AppUpdateRepository(
    private val sessionStore: SecureSessionStore,
) {
    suspend fun loadAndroidPolicy(currentVersionCode: Long): Result<AppUpdatePolicy> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = sessionStore.load()
                    ?: return@runCatching AppUpdatePolicy(
                        kind = AppUpdateKind.None,
                        latestVersionCode = currentVersionCode,
                        minimumVersionCode = currentVersionCode,
                        storeUrl = null,
                    )

                val connection = URL(
                    BuildConfig.SUPABASE_URL +
                        "/rest/v1/player_app_release_config" +
                        "?platform=eq.android" +
                        "&select=latest_version_code,minimum_version_code,store_url" +
                        "&limit=1",
                ).openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    connection.setRequestProperty("Authorization", "Bearer " + session.accessToken)

                    val code = connection.responseCode
                    if (code !in 200..299) {
                        error("Impossible de vérifier la version de l’application.")
                    }

                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(body)
                    if (array.length() == 0) {
                        return@runCatching AppUpdatePolicy(
                            kind = AppUpdateKind.None,
                            latestVersionCode = currentVersionCode,
                            minimumVersionCode = currentVersionCode,
                            storeUrl = null,
                        )
                    }

                    val item = array.getJSONObject(0)
                    val latest = item.getLong("latest_version_code")
                    val minimum = item.getLong("minimum_version_code")
                    val storeUrl = item.optString("store_url")
                        .takeIf { it.isNotBlank() }

                    val kind = when {
                        currentVersionCode < minimum -> AppUpdateKind.Mandatory
                        currentVersionCode < latest -> AppUpdateKind.Optional
                        else -> AppUpdateKind.None
                    }

                    AppUpdatePolicy(
                        kind = kind,
                        latestVersionCode = latest,
                        minimumVersionCode = minimum,
                        storeUrl = storeUrl,
                    )
                } finally {
                    connection.disconnect()
                }
            }
        }
}

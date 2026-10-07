package fr.zyviotv.player.data.sync

import fr.zyviotv.player.BuildConfig
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.shared.sync.CloudSyncRepository
import fr.zyviotv.player.shared.sync.DeviceRegistration
import fr.zyviotv.player.shared.sync.PlaylistSecret
import fr.zyviotv.player.shared.sync.SyncResult
import fr.zyviotv.player.shared.sync.SyncedDevice
import fr.zyviotv.player.shared.sync.SyncedPlaylist
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SupabaseCloudSyncRepository(
    private val sessionStore: SecureSessionStore,
) : CloudSyncRepository {

    override suspend fun registerDevice(device: DeviceRegistration): SyncResult =
        withContext(Dispatchers.IO) {
            val session = sessionStore.load()
                ?: return@withContext SyncResult.Failure("Session absente.")

            val userId = fetchCurrentUserId(session.accessToken)
                ?: return@withContext SyncResult.Failure("Compte utilisateur introuvable.")

            val body = JSONArray()
                .put(
                    JSONObject()
                        .put("user_id", userId)
                        .put("device_uid", device.deviceUid)
                        .put("display_name", device.displayName)
                        .put("platform", device.platform.wireValue)
                        .put("app_version", device.appVersion)
                        .put("last_seen_at", utcNow())
                        .put("updated_at", utcNow())
                )
                .toString()

            val response = request(
                path = "/rest/v1/player_devices?on_conflict=user_id,device_uid",
                method = "POST",
                body = body,
                accessToken = session.accessToken,
                extraHeaders = mapOf(
                    "Prefer" to "resolution=merge-duplicates,return=minimal",
                ),
            )

            if (response.code in 200..299) {
                SyncResult.Success
            } else {
                SyncResult.Failure("Impossible de synchroniser cet appareil.")
            }
        }


    override suspend fun listDevices(): Result<List<SyncedDevice>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = sessionStore.load() ?: error("Session absente.")
                val response = request(
                    path = "/rest/v1/player_devices?select=id,device_uid,display_name,platform,app_version,last_seen_at&order=last_seen_at.desc",
                    method = "GET",
                    body = null,
                    accessToken = session.accessToken,
                )
                if (response.code !in 200..299) {
                    error("Impossible de récupérer les appareils.")
                }

                val array = JSONArray(response.body)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        val platform = fr.zyviotv.player.shared.sync.DevicePlatform.entries
                            .firstOrNull { it.wireValue == item.optString("platform") }
                            ?: fr.zyviotv.player.shared.sync.DevicePlatform.Other
                        add(
                            SyncedDevice(
                                id = item.getString("id"),
                                deviceUid = item.getString("device_uid"),
                                displayName = item.optString("display_name").ifBlank { "Appareil" },
                                platform = platform,
                                appVersion = item.optString("app_version").takeIf { it.isNotBlank() },
                                lastSeenAt = item.optString("last_seen_at").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            }
        }

    override suspend fun renameDevice(
        deviceId: String,
        displayName: String,
    ): SyncResult = withContext(Dispatchers.IO) {
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) {
            return@withContext SyncResult.Failure("Le nom de l’appareil est vide.")
        }
        val session = sessionStore.load()
            ?: return@withContext SyncResult.Failure("Session absente.")
        val response = request(
            path = "/rest/v1/player_devices?id=eq." + deviceId,
            method = "PATCH",
            body = JSONObject()
                .put("display_name", cleanName)
                .put("updated_at", utcNow())
                .toString(),
            accessToken = session.accessToken,
            extraHeaders = mapOf("Prefer" to "return=minimal"),
        )
        if (response.code in 200..299) SyncResult.Success
        else SyncResult.Failure("Impossible de renommer cet appareil.")
    }

    override suspend fun deleteDevice(deviceId: String): SyncResult =
        withContext(Dispatchers.IO) {
            val session = sessionStore.load()
                ?: return@withContext SyncResult.Failure("Session absente.")
            val response = request(
                path = "/rest/v1/player_devices?id=eq." + deviceId,
                method = "DELETE",
                body = null,
                accessToken = session.accessToken,
            )
            if (response.code in 200..299) SyncResult.Success
            else SyncResult.Failure("Impossible de déconnecter cet appareil.")
        }

    override suspend fun listPlaylists(): Result<List<SyncedPlaylist>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = sessionStore.load() ?: error("Session absente.")
                val response = request(
                    path = "/rest/v1/player_playlists?select=id,name,provider_type,server_host,playlist_url_hint,secret_status,is_enabled,priority&order=priority.asc,updated_at.desc",
                    method = "GET",
                    body = null,
                    accessToken = session.accessToken,
                )

                if (response.code !in 200..299) {
                    error("Impossible de récupérer les playlists.")
                }

                val array = JSONArray(response.body)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            SyncedPlaylist(
                                id = item.getString("id"),
                                name = item.getString("name"),
                                providerType = item.getString("provider_type"),
                                serverHost = item.optString("server_host").takeIf { it.isNotBlank() },
                                playlistUrlHint = item.optString("playlist_url_hint").takeIf { it.isNotBlank() },
                                secretStatus = item.optString("secret_status", "not_configured"),
                                isEnabled = item.optBoolean("is_enabled", true),
                                priority = item.optInt("priority", index + 1),
                            ),
                        )
                    }
                }
            }
        }



    override suspend fun createPlaylist(
        name: String,
        providerType: String,
        priority: Int,
        serverHost: String?,
        playlistUrlHint: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = name.trim()
            require(cleanName.isNotBlank()) { "Le nom de la playlist est vide." }
            require(providerType == "xtream" || providerType == "m3u") {
                "Type de fournisseur invalide."
            }

            val session = sessionStore.load() ?: error("Session absente.")
            val userId = fetchCurrentUserId(session.accessToken)
                ?: error("Compte utilisateur introuvable.")

            val body = JSONArray().put(
                JSONObject()
                    .put("user_id", userId)
                    .put("name", cleanName)
                    .put("provider_type", providerType)
                    .put("server_host", serverHost ?: JSONObject.NULL)
                    .put("playlist_url_hint", playlistUrlHint ?: JSONObject.NULL)
                    .put("secret_status", "not_configured")
                    .put("is_enabled", true)
                    .put("priority", priority.coerceIn(1, 10))
                    .put("updated_at", utcNow()),
            ).toString()

            val response = request(
                path = "/rest/v1/player_playlists",
                method = "POST",
                body = body,
                accessToken = session.accessToken,
                extraHeaders = mapOf("Prefer" to "return=representation"),
            )
            if (response.code !in 200..299) {
                error("Impossible d’enregistrer la playlist.")
            }

            val array = JSONArray(response.body)
            if (array.length() == 0) error("Playlist créée sans identifiant.")
            array.getJSONObject(0).getString("id")
        }
    }

    override suspend fun updatePlaylistPriority(
        playlistId: String,
        priority: Int,
    ): SyncResult = updatePlaylistFields(
        playlistId = playlistId,
        fields = JSONObject().put("priority", priority.coerceIn(1, 10)),
        failureMessage = "Impossible de modifier la priorité de la playlist.",
    )

    override suspend fun updatePlaylistEnabled(
        playlistId: String,
        isEnabled: Boolean,
    ): SyncResult = updatePlaylistFields(
        playlistId = playlistId,
        fields = JSONObject().put("is_enabled", isEnabled),
        failureMessage = "Impossible de modifier l’état de la playlist.",
    )

    override suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ): SyncResult {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return SyncResult.Failure("Le nom de la playlist est vide.")
        return updatePlaylistFields(
            playlistId = playlistId,
            fields = JSONObject().put("name", cleanName),
            failureMessage = "Impossible de renommer la playlist.",
        )
    }

    override suspend fun deletePlaylist(playlistId: String): SyncResult =
        withContext(Dispatchers.IO) {
            val session = sessionStore.load()
                ?: return@withContext SyncResult.Failure("Session absente.")
            val response = request(
                path = "/rest/v1/player_playlists?id=eq." + playlistId,
                method = "DELETE",
                body = null,
                accessToken = session.accessToken,
            )
            if (response.code in 200..299) SyncResult.Success
            else SyncResult.Failure("Impossible de supprimer la playlist.")
        }

    private suspend fun updatePlaylistFields(
        playlistId: String,
        fields: JSONObject,
        failureMessage: String,
    ): SyncResult = withContext(Dispatchers.IO) {
        val session = sessionStore.load()
            ?: return@withContext SyncResult.Failure("Session absente.")
        fields.put("updated_at", utcNow())
        val response = request(
            path = "/rest/v1/player_playlists?id=eq." + playlistId,
            method = "PATCH",
            body = fields.toString(),
            accessToken = session.accessToken,
            extraHeaders = mapOf("Prefer" to "return=minimal"),
        )
        if (response.code in 200..299) SyncResult.Success
        else SyncResult.Failure(failureMessage)
    }

    override suspend fun setPlaylistSecret(
        playlistId: String,
        secret: PlaylistSecret,
    ): SyncResult = withContext(Dispatchers.IO) {
        val session = sessionStore.load()
            ?: return@withContext SyncResult.Failure("Session absente.")

        val secretJson = when (secret) {
            is PlaylistSecret.Xtream -> JSONObject()
                .put("provider_type", secret.providerType)
                .put("server_url", secret.serverUrl)
                .put("username", secret.username)
                .put("password", secret.password)
            is PlaylistSecret.M3u -> JSONObject()
                .put("provider_type", secret.providerType)
                .put("url", secret.url)
                .put("xmltv_url", secret.xmlTvUrl ?: JSONObject.NULL)
        }

        val response = request(
            path = "/rest/v1/rpc/player_set_playlist_secret",
            method = "POST",
            body = JSONObject()
                .put("p_playlist_id", playlistId)
                .put("p_secret", secretJson)
                .toString(),
            accessToken = session.accessToken,
        )

        if (response.code in 200..299) SyncResult.Success
        else SyncResult.Failure("Impossible de sécuriser les identifiants du fournisseur.")
    }

    override suspend fun getPlaylistSecret(playlistId: String): Result<PlaylistSecret?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = sessionStore.load() ?: error("Session absente.")
                val response = request(
                    path = "/rest/v1/rpc/player_get_playlist_secret",
                    method = "POST",
                    body = JSONObject().put("p_playlist_id", playlistId).toString(),
                    accessToken = session.accessToken,
                )

                if (response.code !in 200..299) {
                    error("Impossible de restaurer les identifiants du fournisseur.")
                }

                if (response.body.isBlank() || response.body == "null") return@runCatching null
                val json = JSONObject(response.body)
                when (json.optString("provider_type")) {
                    "xtream" -> PlaylistSecret.Xtream(
                        serverUrl = json.getString("server_url"),
                        username = json.getString("username"),
                        password = json.getString("password"),
                    )
                    "m3u" -> PlaylistSecret.M3u(
                        url = json.getString("url"),
                        xmlTvUrl = json.optString("xmltv_url")
                            .takeIf { it.isNotBlank() && it != "null" },
                    )
                    else -> error("Type de fournisseur non reconnu.")
                }
            }
        }

    override suspend fun deletePlaylistSecret(playlistId: String): SyncResult =
        withContext(Dispatchers.IO) {
            val session = sessionStore.load()
                ?: return@withContext SyncResult.Failure("Session absente.")
            val response = request(
                path = "/rest/v1/rpc/player_delete_playlist_secret",
                method = "POST",
                body = JSONObject().put("p_playlist_id", playlistId).toString(),
                accessToken = session.accessToken,
            )

            if (response.code in 200..299) SyncResult.Success
            else SyncResult.Failure("Impossible de supprimer les identifiants du fournisseur.")
        }

    private fun fetchCurrentUserId(accessToken: String): String? {
        val response = request(
            path = "/auth/v1/user",
            method = "GET",
            body = null,
            accessToken = accessToken,
        )

        if (response.code !in 200..299) return null
        return runCatching { JSONObject(response.body).getString("id") }.getOrNull()
    }

    private fun request(
        path: String,
        method: String,
        body: String?,
        accessToken: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val connection = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            extraHeaders.forEach(connection::setRequestProperty)

            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use {
                    it.write(body)
                }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResponse(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun utcNow(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
    )
}

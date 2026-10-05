package fr.zyviotv.player.data.sync

import fr.zyviotv.player.BuildConfig
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.shared.sync.CloudLibraryRepository
import fr.zyviotv.player.shared.sync.FavoriteContentType
import fr.zyviotv.player.shared.sync.ProgressContentType
import fr.zyviotv.player.shared.sync.SyncResult
import fr.zyviotv.player.shared.sync.SyncedFavorite
import fr.zyviotv.player.shared.sync.SyncedWatchProgress
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SupabaseLibrarySyncRepository(
    private val sessionStore: SecureSessionStore,
) : CloudLibraryRepository {

    override suspend fun listFavorites(): Result<List<SyncedFavorite>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = sessionStore.load() ?: error("Session absente.")
                val response = request(
                    path = "/rest/v1/player_favorites?select=playlist_id,content_type,content_id,title,artwork_url&order=updated_at.desc",
                    method = "GET",
                    accessToken = session.accessToken,
                )
                if (response.code !in 200..299) error("Impossible de récupérer les favoris.")

                val array = JSONArray(response.body)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        val type = FavoriteContentType.entries.firstOrNull {
                            it.wireValue == item.getString("content_type")
                        } ?: continue

                        add(
                            SyncedFavorite(
                                playlistId = item.getString("playlist_id"),
                                contentType = type,
                                contentId = item.getString("content_id"),
                                title = item.getString("title"),
                                artworkUrl = item.optString("artwork_url").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            }
        }

    override suspend fun upsertFavorite(favorite: SyncedFavorite): SyncResult =
        withContext(Dispatchers.IO) {
            val session = sessionStore.load() ?: return@withContext SyncResult.Failure("Session absente.")
            val userId = fetchCurrentUserId(session.accessToken)
                ?: return@withContext SyncResult.Failure("Compte utilisateur introuvable.")

            val body = JSONArray().put(
                JSONObject()
                    .put("user_id", userId)
                    .put("playlist_id", favorite.playlistId)
                    .put("content_type", favorite.contentType.wireValue)
                    .put("content_id", favorite.contentId)
                    .put("title", favorite.title)
                    .put("artwork_url", favorite.artworkUrl ?: JSONObject.NULL)
                    .put("updated_at", utcNow()),
            ).toString()

            val response = request(
                path = "/rest/v1/player_favorites?on_conflict=user_id,playlist_id,content_type,content_id",
                method = "POST",
                body = body,
                accessToken = session.accessToken,
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
            )

            if (response.code in 200..299) SyncResult.Success
            else SyncResult.Failure("Impossible de synchroniser le favori.")
        }

    override suspend fun removeFavorite(
        playlistId: String,
        contentType: FavoriteContentType,
        contentId: String,
    ): SyncResult = withContext(Dispatchers.IO) {
        val session = sessionStore.load() ?: return@withContext SyncResult.Failure("Session absente.")
        val path = "/rest/v1/player_favorites?playlist_id=eq." + encoded(playlistId) +
            "&content_type=eq." + encoded(contentType.wireValue) +
            "&content_id=eq." + encoded(contentId)
        val response = request(
            path = path,
            method = "DELETE",
            accessToken = session.accessToken,
        )

        if (response.code in 200..299) SyncResult.Success
        else SyncResult.Failure("Impossible de supprimer le favori.")
    }

    override suspend fun listWatchProgress(limit: Int): Result<List<SyncedWatchProgress>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = sessionStore.load() ?: error("Session absente.")
                val safeLimit = limit.coerceIn(1, 200)
                val path = "/rest/v1/player_watch_progress?select=playlist_id,content_type,content_id,title,series_id,season_number,episode_number,artwork_url,position_ms,duration_ms,completed&order=last_watched_at.desc&limit=" + safeLimit
                val response = request(
                    path = path,
                    method = "GET",
                    accessToken = session.accessToken,
                )
                if (response.code !in 200..299) error("Impossible de récupérer l’historique.")

                val array = JSONArray(response.body)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        val type = ProgressContentType.entries.firstOrNull {
                            it.wireValue == item.getString("content_type")
                        } ?: continue

                        add(
                            SyncedWatchProgress(
                                playlistId = item.getString("playlist_id"),
                                contentType = type,
                                contentId = item.getString("content_id"),
                                title = item.getString("title"),
                                seriesId = item.optString("series_id").takeIf { it.isNotBlank() },
                                seasonNumber = if (item.isNull("season_number")) null else item.getInt("season_number"),
                                episodeNumber = if (item.isNull("episode_number")) null else item.getInt("episode_number"),
                                artworkUrl = item.optString("artwork_url").takeIf { it.isNotBlank() },
                                positionMs = item.getLong("position_ms"),
                                durationMs = if (item.isNull("duration_ms")) null else item.getLong("duration_ms"),
                                completed = item.optBoolean("completed", false),
                            ),
                        )
                    }
                }
            }
        }

    override suspend fun upsertWatchProgress(progress: SyncedWatchProgress): SyncResult =
        withContext(Dispatchers.IO) {
            val session = sessionStore.load() ?: return@withContext SyncResult.Failure("Session absente.")
            val userId = fetchCurrentUserId(session.accessToken)
                ?: return@withContext SyncResult.Failure("Compte utilisateur introuvable.")
            val now = utcNow()

            val body = JSONArray().put(
                JSONObject()
                    .put("user_id", userId)
                    .put("playlist_id", progress.playlistId)
                    .put("content_type", progress.contentType.wireValue)
                    .put("content_id", progress.contentId)
                    .put("title", progress.title)
                    .put("series_id", progress.seriesId ?: JSONObject.NULL)
                    .put("season_number", progress.seasonNumber ?: JSONObject.NULL)
                    .put("episode_number", progress.episodeNumber ?: JSONObject.NULL)
                    .put("artwork_url", progress.artworkUrl ?: JSONObject.NULL)
                    .put("position_ms", progress.positionMs)
                    .put("duration_ms", progress.durationMs ?: JSONObject.NULL)
                    .put("completed", progress.completed)
                    .put("last_watched_at", now)
                    .put("updated_at", now),
            ).toString()

            val response = request(
                path = "/rest/v1/player_watch_progress?on_conflict=user_id,playlist_id,content_type,content_id",
                method = "POST",
                body = body,
                accessToken = session.accessToken,
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
            )

            if (response.code in 200..299) SyncResult.Success
            else SyncResult.Failure("Impossible de synchroniser la progression.")
        }

    override suspend fun removeWatchProgress(
        playlistId: String,
        contentType: ProgressContentType,
        contentId: String,
    ): SyncResult = withContext(Dispatchers.IO) {
        val session = sessionStore.load() ?: return@withContext SyncResult.Failure("Session absente.")
        val path = "/rest/v1/player_watch_progress?playlist_id=eq." + encoded(playlistId) +
            "&content_type=eq." + encoded(contentType.wireValue) +
            "&content_id=eq." + encoded(contentId)
        val response = request(
            path = path,
            method = "DELETE",
            accessToken = session.accessToken,
        )

        if (response.code in 200..299) SyncResult.Success
        else SyncResult.Failure("Impossible de supprimer la progression.")
    }

    private fun fetchCurrentUserId(accessToken: String): String? {
        val response = request(
            path = "/auth/v1/user",
            method = "GET",
            accessToken = accessToken,
        )
        if (response.code !in 200..299) return null
        return runCatching { JSONObject(response.body).getString("id") }.getOrNull()
    }

    private fun request(
        path: String,
        method: String,
        body: String? = null,
        accessToken: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResponse {
        val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + accessToken)
            extraHeaders.forEach(connection::setRequestProperty)

            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { it.write(body) }
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

    private fun encoded(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

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

package fr.zyviotv.player.data.settings

import fr.zyviotv.player.BuildConfig
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.shared.sync.PlayerProfile
import fr.zyviotv.player.shared.sync.PlayerProfileType
import fr.zyviotv.player.shared.sync.ProfileWriteResult
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ProfileRepository(
    private val sessionStore: SecureSessionStore,
) {
    suspend fun ensurePrimaryProfile(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = request(
                path = "/rest/v1/rpc/player_ensure_primary_profile",
                method = "POST",
                body = "{}",
            )
            if (response.code !in 200..299) {
                error("Impossible de préparer votre profil principal.")
            }
            response.body.trim().trim('"').ifBlank {
                error("Profil principal introuvable.")
            }
        }
    }

    suspend fun listProfiles(): Result<List<PlayerProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = request(
                path = "/rest/v1/player_profiles?select=id,name,avatar_key,profile_type,max_age,is_primary&order=is_primary.desc,created_at.asc",
                method = "GET",
            )
            if (response.code !in 200..299) error("Impossible de charger les profils.")

            val array = JSONArray(response.body)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val type = PlayerProfileType.entries.firstOrNull {
                        it.wireValue == item.optString("profile_type")
                    } ?: PlayerProfileType.Standard
                    add(
                        PlayerProfile(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            avatarKey = item.optString("avatar_key", "avatar_01"),
                            type = type,
                            maxAge = if (item.isNull("max_age")) null else item.getInt("max_age"),
                            isPrimary = item.optBoolean("is_primary", false),
                        ),
                    )
                }
            }
        }
    }

    suspend fun createProfile(
        name: String,
        avatarKey: String,
        type: PlayerProfileType,
        maxAge: Int?,
    ): ProfileWriteResult = withContext(Dispatchers.IO) {
        val session = sessionStore.load()
            ?: return@withContext ProfileWriteResult.Failure("Session absente.")
        val userId = fetchCurrentUserId(session.accessToken)
            ?: return@withContext ProfileWriteResult.Failure("Compte utilisateur introuvable.")

        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return@withContext ProfileWriteResult.Failure("Le nom du profil est vide.")
        }

        val body = JSONArray().put(
            JSONObject()
                .put("user_id", userId)
                .put("name", cleanName)
                .put("avatar_key", avatarKey)
                .put("profile_type", type.wireValue)
                .put("max_age", maxAge ?: JSONObject.NULL)
                .put("is_primary", false),
        ).toString()

        val response = request(
            path = "/rest/v1/player_profiles",
            method = "POST",
            body = body,
        )
        if (response.code in 200..299) {
            ProfileWriteResult.Success
        } else {
            val message = if (response.body.contains("profile_limit_reached")) {
                "La limite de 5 profils est atteinte."
            } else {
                "Impossible de créer ce profil."
            }
            ProfileWriteResult.Failure(message)
        }
    }

    suspend fun updateProfile(profile: PlayerProfile): ProfileWriteResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("name", profile.name.trim())
                .put("avatar_key", profile.avatarKey)
                .put("profile_type", profile.type.wireValue)
                .put("max_age", profile.maxAge ?: JSONObject.NULL)
                .put("updated_at", "now()")

            body.remove("updated_at")
            val response = request(
                path = "/rest/v1/player_profiles?id=eq." + encoded(profile.id),
                method = "PATCH",
                body = body.toString(),
            )
            if (response.code in 200..299) {
                ProfileWriteResult.Success
            } else {
                ProfileWriteResult.Failure("Impossible de modifier ce profil.")
            }
        }

    suspend fun deleteProfile(profile: PlayerProfile): ProfileWriteResult =
        withContext(Dispatchers.IO) {
            if (profile.isPrimary) {
                return@withContext ProfileWriteResult.Failure(
                    "Le profil principal ne peut pas être supprimé.",
                )
            }
            val response = request(
                path = "/rest/v1/player_profiles?id=eq." + encoded(profile.id),
                method = "DELETE",
            )
            if (response.code in 200..299) {
                ProfileWriteResult.Success
            } else {
                ProfileWriteResult.Failure("Impossible de supprimer ce profil.")
            }
        }

    private fun fetchCurrentUserId(accessToken: String): String? {
        val response = request(
            path = "/auth/v1/user",
            method = "GET",
            accessTokenOverride = accessToken,
        )
        if (response.code !in 200..299) return null
        return runCatching { JSONObject(response.body).getString("id") }.getOrNull()
    }

    private fun request(
        path: String,
        method: String,
        body: String? = null,
        accessTokenOverride: String? = null,
    ): HttpResponse {
        val session = sessionStore.load() ?: return HttpResponse(401, "")
        val token = accessTokenOverride ?: session.accessToken
        val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doInput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer " + token)

            if (body != null) {
                connection.doOutput = true
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

    private fun encoded(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private data class HttpResponse(
        val code: Int,
        val body: String,
    )
}

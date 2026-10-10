package fr.zyviotv.player.data.settings

import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.network.SupabaseRestClient
import fr.zyviotv.player.shared.sync.PlayerProfile
import fr.zyviotv.player.shared.sync.PlayerProfileType
import fr.zyviotv.player.shared.sync.ProfileWriteResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ProfileRepository(
    private val sessionStore: SecureSessionStore,
) {
    private val client = SupabaseRestClient(sessionStore)

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
            if (response.code !in 200..299) error("PROFILE_LIST_HTTP_${response.code}")

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
        sessionStore.load()
            ?: return@withContext ProfileWriteResult.Failure("Session absente.")
        val userId = fetchCurrentUserId()
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
            val cleanName = profile.name.trim()
            if (cleanName.isBlank()) {
                return@withContext ProfileWriteResult.Failure("Le nom du profil est vide.")
            }
            if (!profile.avatarKey.matches(Regex("avatar_(0[1-9]|1[0-6])"))) {
                return@withContext ProfileWriteResult.Failure("Avatar de profil invalide.")
            }

            val safeType = if (profile.isPrimary) {
                PlayerProfileType.Standard
            } else {
                profile.type
            }
            val safeMaxAge = if (safeType == PlayerProfileType.Child) {
                profile.maxAge
            } else {
                null
            }

            val body = JSONObject()
                .put("name", cleanName)
                .put("avatar_key", profile.avatarKey)
                .put("profile_type", safeType.wireValue)
                .put("max_age", safeMaxAge ?: JSONObject.NULL)

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

    private suspend fun fetchCurrentUserId(): String? {
        val response = request(path = "/auth/v1/user", method = "GET")
        if (response.code !in 200..299) return null
        return runCatching { JSONObject(response.body).getString("id") }.getOrNull()
    }

    // PR #217: token refreshed when expired and after a 401 (shared client).
    private suspend fun request(
        path: String,
        method: String,
        body: String? = null,
    ): SupabaseRestClient.Response = client.request(path = path, method = method, body = body)

    private fun encoded(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

package fr.zyviotv.player.shared.sync

enum class DevicePlatform(val wireValue: String) {
    AndroidPhone("android_phone"),
    AndroidTablet("android_tablet"),
    AndroidTv("android_tv"),
    IPhone("iphone"),
    IPad("ipad"),
    Tizen("tizen"),
    WebOs("webos"),
    Other("other"),
}

data class DeviceRegistration(
    val deviceUid: String,
    val displayName: String,
    val platform: DevicePlatform,
    val appVersion: String,
)

data class SyncedDevice(
    val id: String,
    val deviceUid: String,
    val displayName: String,
    val platform: DevicePlatform,
    val appVersion: String?,
    val lastSeenAt: String?,
)

data class SyncedPlaylist(
    val id: String,
    val name: String,
    val providerType: String,
    val serverHost: String?,
    val playlistUrlHint: String?,
    val secretStatus: String,
    val isEnabled: Boolean,
    val priority: Int = 1,
)

sealed interface PlaylistSecret {
    val providerType: String

    data class Xtream(
        val serverUrl: String,
        val username: String,
        val password: String,
    ) : PlaylistSecret {
        override val providerType: String = "xtream"
        override fun toString(): String = "Xtream(serverUrl=[REDACTED], username=[REDACTED], password=[REDACTED])"
    }

    data class M3u(
        val url: String,
        val xmlTvUrl: String? = null,
    ) : PlaylistSecret {
        override val providerType: String = "m3u"
        override fun toString(): String =
            "M3u(url=[REDACTED], xmlTvUrl=" +
                if (xmlTvUrl.isNullOrBlank()) "null)" else "[REDACTED])"
    }
}

sealed interface SyncResult {
    data object Success : SyncResult
    data class Failure(val message: String) : SyncResult
}

interface CloudSyncRepository {
    suspend fun registerDevice(device: DeviceRegistration): SyncResult
    suspend fun listDevices(): Result<List<SyncedDevice>>
    suspend fun renameDevice(deviceId: String, displayName: String): SyncResult
    suspend fun deleteDevice(deviceId: String): SyncResult
    suspend fun listPlaylists(): Result<List<SyncedPlaylist>>
    suspend fun createPlaylist(
        name: String,
        providerType: String,
        priority: Int,
        serverHost: String? = null,
        playlistUrlHint: String? = null,
    ): Result<String>
    suspend fun updatePlaylistPriority(playlistId: String, priority: Int): SyncResult
    suspend fun updatePlaylistEnabled(playlistId: String, isEnabled: Boolean): SyncResult
    suspend fun renamePlaylist(playlistId: String, name: String): SyncResult
    suspend fun deletePlaylist(playlistId: String): SyncResult
    suspend fun setPlaylistSecret(playlistId: String, secret: PlaylistSecret): SyncResult
    suspend fun getPlaylistSecret(playlistId: String): Result<PlaylistSecret?>
    suspend fun deletePlaylistSecret(playlistId: String): SyncResult
}

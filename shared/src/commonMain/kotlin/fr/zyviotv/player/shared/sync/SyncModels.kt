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

data class SyncedPlaylist(
    val id: String,
    val name: String,
    val providerType: String,
    val serverHost: String?,
    val playlistUrlHint: String?,
    val secretStatus: String,
    val isEnabled: Boolean,
)

sealed interface SyncResult {
    data object Success : SyncResult
    data class Failure(val message: String) : SyncResult
}

interface CloudSyncRepository {
    suspend fun registerDevice(device: DeviceRegistration): SyncResult
    suspend fun listPlaylists(): Result<List<SyncedPlaylist>>
}

package fr.zyviotv.player.shared.account

@JvmInline
value class AccountId(val value: String)

@JvmInline
value class DeviceId(val value: String)

data class AccountSession(
    val accountId: AccountId,
    val deviceId: DeviceId,
    val accessTokenExpiresAtEpochSeconds: Long,
)

data class RegisteredDevice(
    val id: DeviceId,
    val displayName: String,
    val platform: String,
    val lastSeenAtEpochSeconds: Long?,
)

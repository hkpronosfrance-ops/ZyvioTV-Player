package fr.zyviotv.player.shared.sync

enum class PlayerProfileType(val wireValue: String) {
    Standard("standard"),
    Child("child"),
}

data class PlayerProfile(
    val id: String,
    val name: String,
    val avatarKey: String,
    val type: PlayerProfileType,
    val maxAge: Int?,
    val isPrimary: Boolean,
)

sealed interface ProfileWriteResult {
    data object Success : ProfileWriteResult
    data class Failure(val message: String) : ProfileWriteResult
}

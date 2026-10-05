package fr.zyviotv.player.shared.live

data class LiveCategory(
    val id: String,
    val name: String,
)

data class LiveChannel(
    val id: String,
    val name: String,
    val categoryId: String?,
    val logoUrl: String?,
    val streamUrl: String,
)

data class LiveProgram(
    val title: String,
    val startEpochSeconds: Long?,
    val endEpochSeconds: Long?,
)

data class LiveChannelNowNext(
    val channel: LiveChannel,
    val now: LiveProgram?,
    val next: LiveProgram?,
)

package fr.zyviotv.player.shared.playlist

@JvmInline
value class PlaylistId(val value: String)

enum class PlaylistKind {
    M3U,
    XtreamCodes,
}

data class PlaylistReference(
    val id: PlaylistId,
    val name: String,
    val kind: PlaylistKind,
    val isEnabled: Boolean = true,
)

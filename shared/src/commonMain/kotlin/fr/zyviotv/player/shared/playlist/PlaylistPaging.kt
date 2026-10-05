package fr.zyviotv.player.shared.playlist

data class PlaylistPage<T>(
    val items: List<T>,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
)

object PlaylistPaging {
    fun <T> page(
        items: List<T>,
        offset: Int,
        limit: Int,
    ): PlaylistPage<T> {
        val safeOffset = offset.coerceAtLeast(0).coerceAtMost(items.size)
        val safeLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
        val endExclusive = minOf(safeOffset + safeLimit, items.size)

        return PlaylistPage(
            items = items.subList(safeOffset, endExclusive),
            offset = safeOffset,
            limit = safeLimit,
            hasMore = endExclusive < items.size,
        )
    }

    const val MAX_PAGE_SIZE = 200
}

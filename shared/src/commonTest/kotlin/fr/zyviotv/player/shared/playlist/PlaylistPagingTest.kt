package fr.zyviotv.player.shared.playlist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistPagingTest {
    @Test
    fun pageIsBoundedAndReportsMoreItems() {
        val page = PlaylistPaging.page((1..500).toList(), offset = 100, limit = 250)

        assertEquals(200, page.items.size)
        assertEquals(101, page.items.first())
        assertTrue(page.hasMore)
    }

    @Test
    fun lastPageReportsNoMoreItems() {
        val page = PlaylistPaging.page((1..10).toList(), offset = 8, limit = 50)

        assertEquals(listOf(9, 10), page.items)
        assertFalse(page.hasMore)
    }
}

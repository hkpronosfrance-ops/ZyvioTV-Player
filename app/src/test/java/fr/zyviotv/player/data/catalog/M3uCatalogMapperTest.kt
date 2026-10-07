package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.shared.m3u.M3uEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class M3uCatalogMapperTest {
    @Test
    fun duplicateTvgIdsReceiveStableUniqueIds() {
        val snapshot = M3uCatalogMapper.map(
            listOf(
                M3uEntry(
                    name = "Channel A",
                    streamUrl = "https://stream.example/a.ts",
                    tvgId = "duplicate",
                    groupTitle = "France",
                ),
                M3uEntry(
                    name = "Channel B",
                    streamUrl = "https://stream.example/b.ts",
                    tvgId = "duplicate",
                    groupTitle = "France",
                ),
            ),
        )

        assertEquals(2, snapshot.liveChannels.size)
        assertEquals("duplicate", snapshot.liveChannels[0].id)
        assertEquals("duplicate-2", snapshot.liveChannels[1].id)
        assertNotEquals(snapshot.liveChannels[0].id, snapshot.liveChannels[1].id)
    }

    @Test
    fun duplicateTvgIdsKeepOriginalEpgIdForXmlTvMatching() {
        val snapshot = M3uCatalogMapper.map(
            listOf(
                M3uEntry(
                    name = "One",
                    streamUrl = "https://stream.example/1.ts",
                    tvgId = "france2.fr",
                ),
                M3uEntry(
                    name = "Two",
                    streamUrl = "https://stream.example/2.ts",
                    tvgId = "france2.fr",
                ),
            ),
        )

        assertEquals("france2.fr", snapshot.liveChannels[0].epgId)
        assertEquals("france2.fr", snapshot.liveChannels[1].epgId)
        assertNotEquals(snapshot.liveChannels[0].id, snapshot.liveChannels[1].id)
    }

    @Test
    fun groupWhitespaceAndCaseVariantsCollapseIntoOneCategory() {
        val snapshot = M3uCatalogMapper.map(
            listOf(
                M3uEntry(
                    name = "One",
                    streamUrl = "https://stream.example/1.ts",
                    groupTitle = " FRANCE   SPORTS ",
                ),
                M3uEntry(
                    name = "Two",
                    streamUrl = "https://stream.example/2.ts",
                    groupTitle = "france sports",
                ),
            ),
        )

        assertEquals(1, snapshot.liveCategories.size)
        assertEquals("FRANCE SPORTS", snapshot.liveCategories.single().name)
        assertEquals(
            snapshot.liveChannels[0].categoryId,
            snapshot.liveChannels[1].categoryId,
        )
    }
}

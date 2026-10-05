package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.m3u.M3uEntry

object M3uCatalogMapper {
    fun map(entries: List<M3uEntry>): CatalogSnapshot {
        val categoryNames = entries
            .mapNotNull { it.groupTitle?.trim()?.takeIf(String::isNotBlank) }
            .distinct()

        val categories = categoryNames.mapIndexed { index, name ->
            CatalogCategory(id = "m3u-group-$index", name = name)
        }
        val categoryIds = categories.associate { it.name to it.id }

        val channels = entries.mapIndexed { index, entry ->
            CatalogLiveChannel(
                id = entry.tvgId?.takeIf(String::isNotBlank) ?: "m3u-$index",
                name = entry.name,
                categoryId = entry.groupTitle?.let(categoryIds::get),
                logoUrl = entry.logoUrl,
                streamUrl = entry.streamUrl,
            )
        }

        return CatalogSnapshot(
            liveCategories = categories,
            liveChannels = channels,
        )
    }
}

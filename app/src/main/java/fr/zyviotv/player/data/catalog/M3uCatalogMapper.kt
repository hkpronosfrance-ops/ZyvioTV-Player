package fr.zyviotv.player.data.catalog

import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import fr.zyviotv.player.shared.m3u.M3uEntry

object M3uCatalogMapper {
    fun map(entries: List<M3uEntry>): CatalogSnapshot {
        val normalizedGroups = entries.map { entry ->
            entry.groupTitle
                ?.trim()
                ?.replace(Regex("""\s+"""), " ")
                ?.takeIf(String::isNotBlank)
        }

        val categoryNames = normalizedGroups
            .filterNotNull()
            .distinctBy { it.lowercase() }

        val categories = categoryNames.mapIndexed { index, name ->
            CatalogCategory(id = "m3u-group-$index", name = name)
        }
        val categoryIds = categories.associateBy(
            keySelector = { it.name.lowercase() },
            valueTransform = { it.id },
        )

        val usedIds = mutableSetOf<String>()
        val channels = entries.mapIndexedNotNull { index, entry ->
            val streamUrl = entry.streamUrl.trim()
            if (streamUrl.isBlank()) return@mapIndexedNotNull null

            val baseId = entry.tvgId
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: "m3u-$index"
            var channelId = baseId
            var duplicateIndex = 2
            while (!usedIds.add(channelId)) {
                channelId = "$baseId-$duplicateIndex"
                duplicateIndex += 1
            }

            CatalogLiveChannel(
                id = channelId,
                name = entry.name.trim(),
                categoryId = normalizedGroups[index]
                    ?.lowercase()
                    ?.let(categoryIds::get),
                logoUrl = entry.logoUrl?.trim()?.takeIf(String::isNotBlank),
                streamUrl = streamUrl,
            )
        }

        return CatalogSnapshot(
            liveCategories = categories,
            liveChannels = channels,
        )
    }
}

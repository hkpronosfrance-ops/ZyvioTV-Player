package fr.zyviotv.player.shared.m3u

import fr.zyviotv.player.shared.playback.PlaybackSource

object M3uParser {
    data class ParseReport(
        val emitted: Int,
        val headerSeen: Boolean,
        val reachedLimit: Boolean,
        val danglingMetadata: Boolean,
    )

    fun parse(
        content: String,
        maxEntries: Int = Int.MAX_VALUE,
    ): List<M3uEntry> {
        if (maxEntries <= 0) return emptyList()

        val result = ArrayList<M3uEntry>(minOf(maxEntries, 256))
        parseLines(
            lines = content.lineSequence(),
            maxEntries = maxEntries,
        ) { entry ->
            result += entry
        }
        return result
    }

    fun parseLines(
        lines: Sequence<String>,
        maxEntries: Int = Int.MAX_VALUE,
        onEntry: (M3uEntry) -> Unit,
    ): Int = parseLinesDetailed(lines, maxEntries, onEntry).emitted

    fun parseLinesDetailed(
        lines: Sequence<String>,
        maxEntries: Int = Int.MAX_VALUE,
        onEntry: (M3uEntry) -> Unit,
    ): ParseReport {
        if (maxEntries <= 0) {
            return ParseReport(0, headerSeen = false, reachedLimit = true, danglingMetadata = false)
        }

        val iterator = lines.iterator()
        var headerSeen = false
        var metadata: String? = null
        var vlcHeaders: MutableMap<String, String>? = null
        var emitted = 0

        while (iterator.hasNext() && emitted < maxEntries) {
            val line = iterator.next()
                .trim()
                .removePrefix("\uFEFF")
                .trim()
            if (line.isEmpty()) continue

            if (!headerSeen) {
                if (!line.startsWith("#EXTM3U", ignoreCase = true)) {
                    return ParseReport(0, false, false, false)
                }
                headerSeen = true
                continue
            }

            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    metadata = line
                    vlcHeaders = null
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    // Provider access headers announced before the URL line.
                    val option = line.substringAfter(':')
                    val header = VLC_HEADER_OPTIONS[option.substringBefore('=').trim().lowercase()]
                    val value = option.substringAfter('=', "").trim()
                    if (metadata != null && header != null && value.isNotEmpty()) {
                        (vlcHeaders ?: LinkedHashMap<String, String>().also { vlcHeaders = it })[header] = value
                    }
                }
                line.startsWith("#") -> Unit
                metadata != null -> {
                    parseEntry(metadata, line, vlcHeaders.orEmpty())?.let { entry ->
                        onEntry(entry)
                        emitted += 1
                    }
                    metadata = null
                    vlcHeaders = null
                }
            }
        }

        return ParseReport(
            emitted = emitted,
            headerSeen = headerSeen,
            reachedLimit = emitted >= maxEntries,
            danglingMetadata = metadata != null && emitted < maxEntries,
        )
    }

    private fun parseEntry(
        metadata: String,
        streamUrl: String,
        headers: Map<String, String>,
    ): M3uEntry? {
        if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
            return null
        }

        val commaIndex = metadata.lastIndexOf(',')
        val metadataName = if (commaIndex >= 0) {
            metadata.substring(commaIndex + 1).trim()
        } else {
            ""
        }
        val attributes = attributes(metadata)
        val tvgName = attributes["tvg-name"]
        val name = metadataName.ifBlank { tvgName.orEmpty() }.trim()
        if (name.isBlank()) return null

        return M3uEntry(
            name = name,
            streamUrl = PlaybackSource.compose(streamUrl.trim(), headers),
            tvgId = attributes["tvg-id"],
            tvgName = tvgName,
            logoUrl = attributes["tvg-logo"],
            groupTitle = attributes["group-title"],
        )
    }

    private fun attributes(line: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ATTRIBUTE_PATTERN.findAll(line).forEach { match ->
            val key = match.groupValues[1].lowercase()
            if (key !in SUPPORTED_ATTRIBUTES) return@forEach
            val value = match.groupValues.drop(2).firstOrNull(String::isNotEmpty)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: return@forEach
            result[key] = value
        }
        return result
    }

    private val ATTRIBUTE_PATTERN = Regex(
        """(?:^|\s)([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""",
    )
    private val VLC_HEADER_OPTIONS = mapOf(
        "http-user-agent" to "User-Agent",
        "http-referrer" to "Referer",
        "http-referer" to "Referer",
        "http-origin" to "Origin",
    )
    private val SUPPORTED_ATTRIBUTES = setOf("tvg-id", "tvg-name", "tvg-logo", "group-title")
}

package fr.zyviotv.player.shared.m3u

object M3uParser {
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
    ): Int {
        if (maxEntries <= 0) return 0

        val iterator = lines.iterator()
        var headerSeen = false
        var metadata: String? = null
        var emitted = 0

        while (iterator.hasNext() && emitted < maxEntries) {
            val line = iterator.next()
                .trim()
                .removePrefix("\uFEFF")
                .trim()
            if (line.isEmpty()) continue

            if (!headerSeen) {
                if (!line.startsWith("#EXTM3U", ignoreCase = true)) return 0
                headerSeen = true
                continue
            }

            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> metadata = line
                line.startsWith("#") -> Unit
                metadata != null -> {
                    parseEntry(metadata, line)?.let { entry ->
                        onEntry(entry)
                        emitted += 1
                    }
                    metadata = null
                }
            }
        }

        return emitted
    }

    private fun parseEntry(metadata: String, streamUrl: String): M3uEntry? {
        if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
            return null
        }

        val commaIndex = metadata.lastIndexOf(',')
        val metadataName = if (commaIndex >= 0) {
            metadata.substring(commaIndex + 1).trim()
        } else {
            ""
        }
        val tvgName = attribute(metadata, "tvg-name")
        val name = metadataName.ifBlank { tvgName.orEmpty() }.trim()
        if (name.isBlank()) return null

        return M3uEntry(
            name = name,
            streamUrl = streamUrl.trim(),
            tvgId = attribute(metadata, "tvg-id"),
            tvgName = tvgName,
            logoUrl = attribute(metadata, "tvg-logo"),
            groupTitle = attribute(metadata, "group-title"),
        )
    }

    private fun attribute(line: String, name: String): String? {
        val escaped = Regex.escape(name)
        val quoted = Regex(
            """(?:^|\s)$escaped\s*=\s*["']([^"']*)["']""",
            RegexOption.IGNORE_CASE,
        ).find(line)?.groups?.get(1)?.value

        val unquoted = Regex(
            """(?:^|\s)$escaped\s*=\s*([^\s,]+)""",
            RegexOption.IGNORE_CASE,
        ).find(line)?.groups?.get(1)?.value

        return (quoted ?: unquoted)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

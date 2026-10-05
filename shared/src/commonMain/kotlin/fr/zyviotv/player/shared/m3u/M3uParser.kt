package fr.zyviotv.player.shared.m3u

object M3uParser {
    fun parse(content: String): List<M3uEntry> {
        val lines = content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.isEmpty() || !lines.first().startsWith("#EXTM3U")) {
            return emptyList()
        }

        val result = mutableListOf<M3uEntry>()
        var metadata: String? = null

        for (line in lines.drop(1)) {
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> metadata = line
                line.startsWith("#") -> Unit
                metadata != null -> {
                    parseEntry(metadata, line)?.let(result::add)
                    metadata = null
                }
            }
        }

        return result
    }

    private fun parseEntry(metadata: String, streamUrl: String): M3uEntry? {
        if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
            return null
        }

        val commaIndex = metadata.lastIndexOf(',')
        val name = if (commaIndex >= 0) {
            metadata.substring(commaIndex + 1).trim()
        } else {
            ""
        }

        if (name.isBlank()) return null

        return M3uEntry(
            name = name,
            streamUrl = streamUrl,
            tvgId = attribute(metadata, "tvg-id"),
            tvgName = attribute(metadata, "tvg-name"),
            logoUrl = attribute(metadata, "tvg-logo"),
            groupTitle = attribute(metadata, "group-title"),
        )
    }

    private fun attribute(line: String, name: String): String? {
        val match = Regex("""$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(line)
        return match?.groups?.get(1)?.value?.takeIf { it.isNotBlank() }
    }
}

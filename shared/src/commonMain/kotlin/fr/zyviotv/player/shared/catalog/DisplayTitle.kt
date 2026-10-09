package fr.zyviotv.player.shared.catalog

/**
 * Display-only title cleaning (bloc #211).
 *
 * Provider titles often carry technical markers: "(MULTI) FHD", "[4K]",
 * "FR | ...", "VOSTFR", "x265". They are removed only at the edges of the
 * title, only when the whole token (or the whole bracket group) is a known
 * marker, and never when nothing would be left. The raw title stays the
 * source of truth for search, ids, favourites and history.
 */
object DisplayTitle {
    fun clean(raw: String): String {
        var title = raw.trim()
        if (title.isEmpty()) return title
        while (true) {
            val next = stripOnce(title)
            if (next == title || next.isEmpty()) break
            title = next
        }
        // A title made only of markers ("(MULTI) FHD") is shown as provided.
        if (title.isEmpty() || isMarkerGroup(title)) return raw.trim()
        return title
    }

    private fun stripOnce(title: String): String {
        trailingGroup(title)?.let { return it }
        leadingGroup(title)?.let { return it }
        trailingToken(title)?.let { return it }
        leadingToken(title)?.let { return it }
        return title
    }

    /** "Title (MULTI)" or "Title [FHD 4K]" -> "Title". */
    private fun trailingGroup(title: String): String? {
        val close = title.last()
        val open = OPENING[close] ?: return null
        val start = title.lastIndexOf(open)
        if (start <= 0) return null
        if (!isMarkerGroup(title.substring(start + 1, title.length - 1))) return null
        return trimSeparators(title.substring(0, start))
    }

    /** "[VOSTFR] Title" -> "Title". */
    private fun leadingGroup(title: String): String? {
        val open = title.first()
        val close = CLOSING[open] ?: return null
        val end = title.indexOf(close)
        if (end < 0 || end == title.length - 1) return null
        if (!isMarkerGroup(title.substring(1, end))) return null
        return trimSeparators(title.substring(end + 1))
    }

    /** "Title FHD" or "Title - VF" -> "Title". */
    private fun trailingToken(title: String): String? {
        val split = title.indexOfLast { it in SEPARATORS }
        if (split <= 0) return null
        val token = title.substring(split + 1)
        if (!isMarker(token)) return null
        val head = title.substring(0, split + 1)
        val separatorRun = head.takeLastWhile { it in SEPARATORS }
        if (!isUnambiguous(token, separatorRun)) return null
        return trimSeparators(head)
    }

    /** "FR | Title" or "4K - Title" -> "Title". */
    private fun leadingToken(title: String): String? {
        val split = title.indexOfFirst { it in SEPARATORS }
        if (split <= 0 || split == title.length - 1) return null
        val token = title.substring(0, split)
        if (!isMarker(token)) return null
        val tail = title.substring(split)
        val separatorRun = tail.takeWhile { it in SEPARATORS }
        if (!isUnambiguous(token, separatorRun)) return null
        return trimSeparators(tail)
    }

    /**
     * Markers that are also ordinary words ("En attendant...", "French Kiss",
     * "Charlotte's Web") are removed only when a strong separator such as
     * "|" or " - " sets them apart, never after a plain space.
     */
    private fun isUnambiguous(token: String, separatorRun: String): Boolean =
        token.uppercase() !in WORD_LIKE_MARKERS || separatorRun.any { it in STRONG_SEPARATORS }

    private fun isMarkerGroup(content: String): Boolean {
        val tokens = content.split(*SEPARATORS.toCharArray()).filter(String::isNotEmpty)
        return tokens.isNotEmpty() && tokens.all(::isMarker)
    }

    private fun isMarker(token: String): Boolean = token.uppercase() in MARKERS

    private fun trimSeparators(value: String): String =
        value.trim { it in SEPARATORS || it.isWhitespace() }

    private const val SEPARATORS = " |-_.:/"
    private const val STRONG_SEPARATORS = "|-:/"
    private val OPENING = mapOf(')' to '(', ']' to '[', '}' to '{')
    private val CLOSING = mapOf('(' to ')', '[' to ']', '{' to '}')

    private val MARKERS = setOf(
        // Languages and subtitles
        "MULTI", "MULTISUB", "MULTI-SUB", "VF", "VFF", "VFQ", "VFI", "VF2", "VO", "VOST", "VOSTFR", "VOSTA",
        "TRUEFRENCH", "FRENCH", "SUBFRENCH", "FR", "EN", "ENG",
        // Definitions
        "SD", "HD", "FHD", "UHD", "QHD", "4K", "8K", "480P", "576P", "720P", "1080P", "1080I", "2160P",
        "HDR", "HDR10", "HDR10+", "DV", "DOLBY", "LQ", "HQ",
        // Codecs and sources
        "HEVC", "H264", "H.264", "H265", "H.265", "X264", "X265", "AVC", "10BIT", "AAC", "AC3", "EAC3", "DTS", "ATMOS",
        "WEB", "WEBRIP", "WEB-DL", "WEBDL", "BLURAY", "BDRIP", "BRRIP", "DVDRIP", "HDTV", "REMUX",
    )

    private val WORD_LIKE_MARKERS = setOf(
        "FR", "EN", "ENG", "VO", "VF", "FRENCH", "TRUEFRENCH", "DV", "DOLBY", "WEB",
        "AVC", "AAC", "DTS", "ATMOS", "REMUX",
    )
}

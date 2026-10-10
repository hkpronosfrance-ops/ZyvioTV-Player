package fr.zyviotv.player.data.store

/**
 * Bloc #213 (PR B): a catalogue restored from the Room store does not hold
 * its stream URLs in memory. Each item carries this opaque reference instead
 * (`zyvio-store:<kind>:<generationId>:<playlist>:<id>[:<seriesId>]`), and the
 * URL is decrypted only when playback is requested
 * (`CatalogStore.resolveSource`). A reference contains no host, credential or
 * URL, and never reaches the player: it is resolved first.
 *
 * PR #215: the strings are escaped (`%` → `%25`, `:` → `%3A`) instead of
 * hex-encoded with `String.format` per byte, which cost ~1 million format
 * calls for a 22 841-item catalogue at startup. Ids rarely contain either
 * character, so encoding is usually a plain concatenation.
 */
data class CatalogSourceRef(
    val kind: CatalogKind,
    val generationId: Long,
    val playlistId: String,
    val id: String,
    /** Only for episodes, whose ids are unique inside their series. */
    val seriesId: String? = null,
) {
    fun encode(): String {
        val head = prefix(kind, generationId, playlistId)
        return if (seriesId == null) head + escape(id) else head + escape(id) + SEPARATOR + escape(seriesId)
    }

    companion object {
        private const val PREFIX = "zyvio-store:"
        private const val SEPARATOR = ':'

        fun isRef(value: String): Boolean = value.startsWith(PREFIX)

        /**
         * Everything before the item id; the same for every item of one kind
         * in one generation, so a reader computes it once (see [ofItem]).
         */
        fun prefix(kind: CatalogKind, generationId: Long, playlistId: String): String =
            PREFIX + kind.wire + SEPARATOR + generationId + SEPARATOR + escape(playlistId) + SEPARATOR

        /** Reference of a live channel or film from a precomputed [prefix]. */
        fun ofItem(prefix: String, id: String): String = prefix + escape(id)

        /** Null when [value] is not a well-formed reference. */
        fun parse(value: String): CatalogSourceRef? {
            if (!isRef(value)) return null
            val parts = value.removePrefix(PREFIX).split(SEPARATOR)
            if (parts.size !in 4..5) return null
            return runCatching {
                val kind = CatalogKind.entries.first { it.wire == parts[0] }
                CatalogSourceRef(
                    kind = kind,
                    generationId = parts[1].toLong(),
                    playlistId = unescape(parts[2]),
                    id = unescape(parts[3]),
                    seriesId = parts.getOrNull(4)?.let(::unescape),
                ).takeIf { (it.seriesId != null) == (kind == CatalogKind.Episode) }
            }.getOrNull()
        }

        // `%` first, so an escaped `:` is never escaped twice.
        internal fun escape(value: String): String =
            if (value.indexOf('%') < 0 && value.indexOf(SEPARATOR) < 0) {
                value
            } else {
                value.replace("%", "%25").replace(":", "%3A")
            }

        // `%3A` first: an original "%3A" was encoded as "%253A", which holds no "%3A".
        internal fun unescape(value: String): String =
            if (value.indexOf('%') < 0) value else value.replace("%3A", ":").replace("%25", "%")
    }
}

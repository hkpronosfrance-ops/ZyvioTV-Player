package fr.zyviotv.player.data.store

/**
 * Bloc #213 (PR B): a catalogue restored from the Room store does not hold
 * its stream URLs in memory. Each item carries this opaque reference instead
 * (`zyvio-store:<kind>:<generationId>:<playlist>:<id>[:<seriesId>]`, the
 * strings hex-encoded), and the URL is decrypted only when playback is
 * requested (`CatalogStore.resolveSource`). A reference contains no host,
 * credential or URL, and never reaches the player: it is resolved first.
 */
data class CatalogSourceRef(
    val kind: CatalogKind,
    val generationId: Long,
    val playlistId: String,
    val id: String,
    /** Only for episodes, whose ids are unique inside their series. */
    val seriesId: String? = null,
) {
    fun encode(): String = buildString {
        append(PREFIX)
        append(kind.wire)
        append(SEPARATOR)
        append(generationId)
        append(SEPARATOR)
        append(playlistId.hex())
        append(SEPARATOR)
        append(id.hex())
        if (seriesId != null) {
            append(SEPARATOR)
            append(seriesId.hex())
        }
    }

    companion object {
        private const val PREFIX = "zyvio-store:"
        private const val SEPARATOR = ':'

        fun isRef(value: String): Boolean = value.startsWith(PREFIX)

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
                    playlistId = parts[2].unhex(),
                    id = parts[3].unhex(),
                    seriesId = parts.getOrNull(4)?.unhex(),
                ).takeIf { (it.seriesId != null) == (kind == CatalogKind.Episode) }
            }.getOrNull()
        }

        // Hex rather than java.util.Base64, which needs API 26 (minSdk is 24).
        private fun String.hex(): String =
            toByteArray(Charsets.UTF_8).joinToString("") { byte -> "%02x".format(byte) }

        private fun String.unhex(): String {
            require(length % 2 == 0)
            return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
                .toString(Charsets.UTF_8)
        }
    }
}

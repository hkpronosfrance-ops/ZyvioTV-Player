package fr.zyviotv.player.shared.playback

import fr.zyviotv.player.shared.security.SecretLoggingPolicy

/**
 * A stream line as stored in the catalog, split into the URL the player opens
 * and the HTTP headers the provider requires to serve it.
 *
 * M3U playlists carry those headers either with the Kodi convention
 * (`url|User-Agent=...&Referer=...`) or with `#EXTVLCOPT:http-user-agent=...`
 * lines; the parser folds the latter into the former, so the catalog and its
 * encrypted cache keep a single string per stream (bloc #208). Only a small
 * allow-list of headers is honoured, and values never reach the logs.
 */
data class PlaybackSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) {
    override fun toString(): String =
        "PlaybackSource(url=${SecretLoggingPolicy.redactUrlForLogs(url)}, headers=${headers.keys})"

    companion object {
        fun parse(streamUrl: String): PlaybackSource {
            val trimmed = streamUrl.trim()
            val pipe = trimmed.indexOf('|')
            if (pipe < 0) return PlaybackSource(trimmed)

            val headers = LinkedHashMap<String, String>()
            trimmed.substring(pipe + 1).split('&').forEach { pair ->
                val name = SUPPORTED_HEADERS[pair.substringBefore('=', "").trim().lowercase()]
                    ?: return@forEach
                val value = percentDecode(pair.substringAfter('=', "")).trim()
                if (value.isNotEmpty() && value.none { it == '\r' || it == '\n' }) {
                    headers[name] = value
                }
            }
            // A '|' that does not introduce a supported header belongs to the
            // URL itself: keep the line untouched rather than truncating it.
            if (headers.isEmpty()) return PlaybackSource(trimmed)
            return PlaybackSource(url = trimmed.substring(0, pipe).trim(), headers = headers)
        }

        /** Inverse of [parse] for headers announced outside the URL line. */
        fun compose(url: String, headers: Map<String, String>): String {
            val accepted = headers.entries.mapNotNull { (name, value) ->
                val canonical = SUPPORTED_HEADERS[name.trim().lowercase()] ?: return@mapNotNull null
                val clean = value.trim()
                if (clean.isEmpty() || clean.any { it == '\r' || it == '\n' }) null
                else canonical to clean
            }
            if (accepted.isEmpty() || '|' in url) return url
            return url + "|" + accepted.joinToString("&") { (name, value) ->
                name + "=" + percentEncode(value)
            }
        }

        private val SUPPORTED_HEADERS = mapOf(
            "user-agent" to "User-Agent",
            "referer" to "Referer",
            "referrer" to "Referer",
            "origin" to "Origin",
        )

        private const val HEX = "0123456789ABCDEF"

        private fun percentEncode(value: String): String = buildString {
            value.encodeToByteArray().forEach { byte ->
                val code = byte.toInt() and 0xFF
                val char = code.toChar()
                if (code in 0x20..0x7E && char !in "%&=|") {
                    append(char)
                } else {
                    append('%')
                    append(HEX[code shr 4])
                    append(HEX[code and 0x0F])
                }
            }
        }

        private fun percentDecode(value: String): String {
            if ('%' !in value) return value
            val bytes = ArrayList<Byte>(value.length)
            var index = 0
            while (index < value.length) {
                val char = value[index]
                val high = if (char == '%' && index + 2 < value.length) {
                    HEX.indexOf(value[index + 1].uppercaseChar())
                } else {
                    -1
                }
                val low = if (high >= 0) HEX.indexOf(value[index + 2].uppercaseChar()) else -1
                if (high >= 0 && low >= 0) {
                    bytes += ((high shl 4) or low).toByte()
                    index += 3
                } else {
                    char.toString().encodeToByteArray().forEach { bytes += it }
                    index += 1
                }
            }
            return bytes.toByteArray().decodeToString()
        }
    }
}

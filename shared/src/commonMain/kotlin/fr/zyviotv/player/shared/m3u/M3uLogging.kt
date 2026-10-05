package fr.zyviotv.player.shared.m3u

object M3uLogging {
    private val sensitiveKeys = setOf(
        "username",
        "password",
        "token",
        "auth",
        "key",
    )

    fun redactUrl(raw: String): String {
        val queryStart = raw.indexOf('?')
        if (queryStart < 0) return raw

        val base = raw.substring(0, queryStart)
        val query = raw.substring(queryStart + 1)

        val redacted = query
            .split('&')
            .joinToString("&") { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@joinToString part

                val key = part.substring(0, separator)
                if (key.lowercase() in sensitiveKeys) {
                    "$key=***"
                } else {
                    part
                }
            }

        return "$base?$redacted"
    }
}

package fr.zyviotv.player.shared.security

object SecretLoggingPolicy {
    private val credentialQueryKeys = setOf(
        "username",
        "password",
        "token",
        "access_token",
        "auth",
    )

    fun redactUrlForLogs(raw: String): String {
        val questionMark = raw.indexOf('?')
        if (questionMark < 0) return raw

        val base = raw.substring(0, questionMark)
        val query = raw.substring(questionMark + 1)
        val redacted = query
            .split('&')
            .joinToString("&") { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@joinToString part

                val key = part.substring(0, separator)
                if (key.lowercase() in credentialQueryKeys) "$key=***" else part
            }

        return "$base?$redacted"
    }
}

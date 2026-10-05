package fr.zyviotv.player.shared.security

object SecretLoggingPolicy {
    private val credentialQueryKeys = setOf(
        "username",
        "password",
        "token",
        "access_token",
        "auth",
    )

    private val userInfoRegex = Regex(
        """(https?://)[^/@\s:]+:[^/@\s]+@""",
        RegexOption.IGNORE_CASE,
    )

    private val xtreamPathRegex = Regex(
        """(/(?:live|movie|series)/)([^/?#]+)(/)([^/?#]+)(/)""",
        RegexOption.IGNORE_CASE,
    )

    fun redactUrlForLogs(raw: String): String {
        val withoutUserInfo = userInfoRegex.replace(raw) { match ->
            "${match.groupValues[1]}***:***@"
        }
        val withoutPathCredentials = xtreamPathRegex.replace(withoutUserInfo) { match ->
            "${match.groupValues[1]}***${match.groupValues[3]}***${match.groupValues[5]}"
        }

        val questionMark = withoutPathCredentials.indexOf('?')
        if (questionMark < 0) return withoutPathCredentials

        val base = withoutPathCredentials.substring(0, questionMark)
        val query = withoutPathCredentials.substring(questionMark + 1)
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

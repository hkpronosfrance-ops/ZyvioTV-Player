package fr.zyviotv.player.shared.security

object SecretLoggingPolicy {
    private val credentialQueryKeys = setOf(
        "username",
        "password",
        "token",
        "access_token",
        "auth",
    )

    private val userInfoRegex = Regex("""(https?://)[^/@s:]+:[^/@s]+@""", RegexOption.IGNORE_CASE)
    private val xtreamPathRegex = Regex(
        """(/(?:live|movie|series)/)([^/?#]+)(/)([^/?#]+)(/)""",
        RegexOption.IGNORE_CASE,
    )

    fun redactUrlForLogs(raw: String): String {
        val withoutUserInfo = raw.replace(userInfoRegex, "$1***:***@")
        val withoutPathCredentials = withoutUserInfo.replace(xtreamPathRegex, "$1***$3***$5")

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

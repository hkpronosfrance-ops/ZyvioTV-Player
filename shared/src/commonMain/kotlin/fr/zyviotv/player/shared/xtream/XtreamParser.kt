package fr.zyviotv.player.shared.xtream

object XtreamParser {
    fun parseProfile(json: String): XtreamProfile? {
        val userInfo = objectSection(json, "user_info") ?: return null
        val serverInfo = objectSection(json, "server_info") ?: return null

        val username = stringValue(userInfo, "username") ?: return null
        val status = stringValue(userInfo, "status") ?: "Unknown"

        val account = XtreamAccountInfo(
            username = username,
            status = status,
            isTrial = stringValue(userInfo, "is_trial") == "1",
            activeConnections = stringValue(userInfo, "active_cons")?.toIntOrNull(),
            maxConnections = stringValue(userInfo, "max_connections")?.toIntOrNull(),
            createdAtEpochSeconds = stringValue(userInfo, "created_at")?.toLongOrNull(),
            expiresAtEpochSeconds = stringValue(userInfo, "exp_date")?.toLongOrNull(),
        )

        val server = XtreamServerInfo(
            url = stringValue(serverInfo, "url").orEmpty(),
            port = stringValue(serverInfo, "port")?.toIntOrNull(),
            httpsPort = stringValue(serverInfo, "https_port")?.toIntOrNull(),
            serverProtocol = stringValue(serverInfo, "server_protocol"),
            timezone = stringValue(serverInfo, "timezone"),
        )

        return XtreamProfile(account = account, server = server)
    }

    private fun objectSection(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"" + key + "\"")
        if (keyIndex < 0) return null

        val open = json.indexOf('{', keyIndex)
        if (open < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (index in open until json.length) {
            val char = json[index]

            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }

            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return json.substring(open, index + 1)
                }
            }
        }

        return null
    }

    private fun stringValue(section: String, key: String): String? {
        val escapedKey = Regex.escape(key)
        val pattern = Regex("\"" + escapedKey + "\"\\s*:\\s*(?:\"((?:\\\\.|[^\"])*)\"|([^,}\\s]+))")
        val match = pattern.find(section) ?: return null
        val quoted = match.groups[1]?.value
        val raw = quoted ?: match.groups[2]?.value

        return raw
            ?.takeUnless { it == "null" }
            ?.replace("\\/", "/")
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }
}

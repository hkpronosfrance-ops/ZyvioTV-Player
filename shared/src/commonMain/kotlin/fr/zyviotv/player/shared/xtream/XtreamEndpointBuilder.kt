package fr.zyviotv.player.shared.xtream

object XtreamEndpointBuilder {
    fun playerApi(credentials: XtreamCredentials): String {
        val base = normalizedServerBase(credentials.serverUrl)
        return base + "/player_api.php"
    }

    fun authenticatedPlayerApi(
        credentials: XtreamCredentials,
        action: String? = null,
        extraParams: Map<String, String> = emptyMap(),
    ): String {
        val base = playerApi(credentials)
        val query = buildList {
            add("username=" + encodeQuery(credentials.username))
            add("password=" + encodeQuery(credentials.password))
            if (!action.isNullOrBlank()) add("action=" + encodeQuery(action))
            extraParams.toSortedMap().forEach { (key, value) ->
                add(encodeQuery(key) + "=" + encodeQuery(value))
            }
        }.joinToString("&")
        return "$base?$query"
    }

    fun liveStream(
        credentials: XtreamCredentials,
        streamId: String,
        extension: String = "ts",
    ): String {
        val base = normalizedServerBase(credentials.serverUrl)
        return base + "/live/" + encodePath(credentials.username) + "/" +
            encodePath(credentials.password) + "/" + encodePath(streamId) + "." + encodePath(extension)
    }

    fun movieStream(
        credentials: XtreamCredentials,
        streamId: String,
        extension: String,
    ): String {
        val base = normalizedServerBase(credentials.serverUrl)
        return base + "/movie/" + encodePath(credentials.username) + "/" +
            encodePath(credentials.password) + "/" + encodePath(streamId) + "." + encodePath(extension)
    }

    fun seriesStream(
        credentials: XtreamCredentials,
        streamId: String,
        extension: String,
    ): String {
        val base = normalizedServerBase(credentials.serverUrl)
        return base + "/series/" + encodePath(credentials.username) + "/" +
            encodePath(credentials.password) + "/" + encodePath(streamId) + "." + encodePath(extension)
    }

    fun redactedForLogs(url: String): String {
        val parts = url.split('/')
        if (parts.size < 3) return redactQuery(url)

        val mutable = parts.toMutableList()
        val markers = setOf("live", "movie", "series")

        for (index in mutable.indices) {
            if (mutable[index] in markers && index + 2 < mutable.size) {
                mutable[index + 1] = "***"
                mutable[index + 2] = "***"
                return mutable.joinToString("/")
            }
        }

        return redactQuery(url)
    }

    private fun redactQuery(url: String): String =
        url.replace(
            Regex("([?&]username=)[^&]+", RegexOption.IGNORE_CASE),
            "$1***",
        ).replace(
            Regex("([?&]password=)[^&]+", RegexOption.IGNORE_CASE),
            "$1***",
        )

    private fun encodePath(value: String): String =
        encodeComponent(value.trim())

    internal fun normalizedServerBase(value: String): String {
        var base = value.trim()
            .substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
        val knownEndpoint = KNOWN_ENDPOINTS.firstOrNull { endpoint ->
            base.endsWith(endpoint, ignoreCase = true)
        }
        if (knownEndpoint != null) {
            base = base.dropLast(knownEndpoint.length).trimEnd('/')
        }
        return base
    }

    private fun encodeQuery(value: String): String =
        encodeComponent(value.trim())

    private fun encodeComponent(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val number = byte.toInt() and 0xff
            val char = number.toChar()
            val unreserved =
                (char in 'a'..'z') ||
                    (char in 'A'..'Z') ||
                    (char in '0'..'9') ||
                    char == '-' || char == '_' || char == '.' || char == '~'

            if (unreserved) {
                append(char)
            } else {
                append('%')
                append(HEX[(number shr 4) and 0x0f])
                append(HEX[number and 0x0f])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
    private val KNOWN_ENDPOINTS = listOf("/player_api.php", "/get.php")
}

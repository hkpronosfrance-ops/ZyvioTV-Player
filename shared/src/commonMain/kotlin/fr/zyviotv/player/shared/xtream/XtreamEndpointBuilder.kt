package fr.zyviotv.player.shared.xtream

object XtreamEndpointBuilder {
    fun playerApi(credentials: XtreamCredentials): String {
        val base = credentials.serverUrl.trim().trimEnd('/')
        return base + "/player_api.php"
    }

    fun liveStream(
        credentials: XtreamCredentials,
        streamId: String,
        extension: String = "ts",
    ): String {
        val base = credentials.serverUrl.trim().trimEnd('/')
        return base + "/live/" + encodePath(credentials.username) + "/" +
            encodePath(credentials.password) + "/" + encodePath(streamId) + "." + encodePath(extension)
    }

    fun movieStream(
        credentials: XtreamCredentials,
        streamId: String,
        extension: String,
    ): String {
        val base = credentials.serverUrl.trim().trimEnd('/')
        return base + "/movie/" + encodePath(credentials.username) + "/" +
            encodePath(credentials.password) + "/" + encodePath(streamId) + "." + encodePath(extension)
    }

    fun seriesStream(
        credentials: XtreamCredentials,
        streamId: String,
        extension: String,
    ): String {
        val base = credentials.serverUrl.trim().trimEnd('/')
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
        value.trim()
            .replace("%", "%25")
            .replace("/", "%2F")
            .replace("?", "%3F")
            .replace("#", "%23")
            .replace(" ", "%20")
}

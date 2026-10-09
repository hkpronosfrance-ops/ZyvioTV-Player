package fr.zyviotv.player.data.catalog

/** Shape of an Xtream response body, read from its first characters only. */
enum class XtreamBodyKind(val logName: String) {
    JsonArray("json_array"),
    EmptyArray("empty_array"),
    JsonObject("json_object"),
    Html("html"),
    Empty("empty"),
    NotJson("not_json"),
    ;

    companion object {
        fun of(body: String): XtreamBodyKind {
            val start = body.indexOfFirst { !it.isWhitespace() && it != '﻿' }
            if (start < 0) return Empty
            return when (body[start]) {
                '[' -> {
                    val next = body.indexOfFirst(start + 1) { !it.isWhitespace() }
                    if (next >= 0 && body[next] == ']') EmptyArray else JsonArray
                }
                '{' -> JsonObject
                '<' -> Html
                else -> if (body.trim() == "null") Empty else NotJson
            }
        }

        private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
            for (index in from until length) if (predicate(this[index])) return index
            return -1
        }
    }
}

/** The six Xtream catalogue lists, checked one by one. */
enum class XtreamCatalogList(val action: String, val logName: String, val label: String) {
    LiveCategories("get_live_categories", "live_categories", "les catégories TV"),
    LiveStreams("get_live_streams", "live_streams", "les chaînes TV"),
    MovieCategories("get_vod_categories", "movie_categories", "les catégories de films"),
    Movies("get_vod_streams", "movies", "les films"),
    SeriesCategories("get_series_categories", "series_categories", "les catégories de séries"),
    Series("get_series", "series", "les séries"),
}

enum class XtreamListOutcome(val logName: String, val accepted: Boolean) {
    Valid("valid", accepted = true),

    /** Really empty: accepted, a provider may offer no film or no series. */
    Empty("empty", accepted = true),

    /** Empty while the previous validated catalogue of this playlist was not. */
    SuspiciousEmpty("suspicious_empty", accepted = false),

    /** HTML page, JSON object (e.g. refused authentication) or unreadable body. */
    InvalidBody("invalid_body", accepted = false),

    /** A non-empty array of which no entry could be used. */
    NoUsableEntry("no_usable_entry", accepted = false),
}

/**
 * Bloc #211: per-endpoint validation of an Xtream catalogue refresh. Any
 * rejected list makes the whole refresh fail, so the catalogue already
 * validated (and its cache) is kept untouched.
 */
object XtreamCatalogValidation {
    /**
     * @param previousCount size of this list in the previous validated
     *   catalogue of the same playlist, or null when there is none.
     */
    fun check(
        bodyKind: XtreamBodyKind,
        rawCount: Int,
        usableCount: Int,
        previousCount: Int?,
    ): XtreamListOutcome = when (bodyKind) {
        XtreamBodyKind.Html,
        XtreamBodyKind.JsonObject,
        XtreamBodyKind.NotJson,
        -> XtreamListOutcome.InvalidBody

        XtreamBodyKind.Empty,
        XtreamBodyKind.EmptyArray,
        -> emptyOutcome(previousCount)

        XtreamBodyKind.JsonArray -> when {
            rawCount == 0 -> emptyOutcome(previousCount)
            usableCount == 0 -> XtreamListOutcome.NoUsableEntry
            else -> XtreamListOutcome.Valid
        }
    }

    private fun emptyOutcome(previousCount: Int?): XtreamListOutcome =
        if (previousCount != null && previousCount > 0) {
            XtreamListOutcome.SuspiciousEmpty
        } else {
            XtreamListOutcome.Empty
        }

    /** French message shown when a list is rejected; never contains provider data. */
    fun message(list: XtreamCatalogList, outcome: XtreamListOutcome): String = when (outcome) {
        XtreamListOutcome.SuspiciousEmpty ->
            "Le fournisseur IPTV a renvoyé une liste vide pour ${list.label} alors que le catalogue précédent en contenait. Le catalogue validé est conservé."
        XtreamListOutcome.InvalidBody,
        XtreamListOutcome.NoUsableEntry,
        ->
            "Le fournisseur IPTV a renvoyé une réponse inattendue pour ${list.label}. Le catalogue validé est conservé."
        XtreamListOutcome.Valid,
        XtreamListOutcome.Empty,
        -> ""
    }
}

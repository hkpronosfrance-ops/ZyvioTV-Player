package fr.zyviotv.player.shared.search

import fr.zyviotv.player.shared.catalog.CatalogSnapshot

enum class SearchKind { Live, Movie, Series }

data class SearchResultItem(
    val id: String,
    val title: String,
    val kind: SearchKind,
    val imageUrl: String?,
    val category: String?,
    val channelNumber: String? = null,
)

object CatalogSearchEngine {
    const val MIN_QUERY_LENGTH = 2
    const val DEFAULT_LIMIT = 100

    fun search(
        snapshot: CatalogSnapshot,
        rawQuery: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<SearchResultItem> {
        val query = normalize(rawQuery)
        if (query.length < MIN_QUERY_LENGTH || limit <= 0) return emptyList()

        val liveCategories = snapshot.liveCategories.associate { it.id to it.name }
        val movieCategories = snapshot.movieCategories.associate { it.id to it.name }
        val seriesCategories = snapshot.seriesCategories.associate { it.id to it.name }

        return buildList {
            snapshot.liveChannels.forEach { channel ->
                val number = channel.name.leadingChannelNumber()
                val searchable = listOfNotNull(channel.name, number, liveCategories[channel.categoryId])
                    .joinToString(" ")
                if (normalize(searchable).contains(query)) {
                    add(
                        SearchResultItem(
                            id = channel.id,
                            title = channel.name,
                            kind = SearchKind.Live,
                            imageUrl = channel.logoUrl,
                            category = liveCategories[channel.categoryId],
                            channelNumber = number,
                        ),
                    )
                }
            }

            snapshot.movies.forEach { movie ->
                val searchable = listOfNotNull(movie.title, movieCategories[movie.categoryId]).joinToString(" ")
                if (normalize(searchable).contains(query)) {
                    add(
                        SearchResultItem(
                            id = movie.id,
                            title = movie.title,
                            kind = SearchKind.Movie,
                            imageUrl = movie.posterUrl,
                            category = movieCategories[movie.categoryId],
                        ),
                    )
                }
            }

            snapshot.series.forEach { series ->
                val searchable = listOfNotNull(series.title, seriesCategories[series.categoryId]).joinToString(" ")
                if (normalize(searchable).contains(query)) {
                    add(
                        SearchResultItem(
                            id = series.id,
                            title = series.title,
                            kind = SearchKind.Series,
                            imageUrl = series.posterUrl,
                            category = seriesCategories[series.categoryId],
                        ),
                    )
                }
            }
        }
            .sortedWith(
                compareBy<SearchResultItem> {
                    val title = normalize(it.title)
                    when {
                        title == query -> 0
                        title.startsWith(query) -> 1
                        else -> 2
                    }
                }.thenBy { normalize(it.title) },
            )
            .take(limit)
    }

    fun normalize(value: String): String =
        value
            .trim()
            .lowercase()
            .map { char ->
                when (char) {
                    'à', 'á', 'â', 'ä', 'ã', 'å' -> 'a'
                    'ç' -> 'c'
                    'è', 'é', 'ê', 'ë' -> 'e'
                    'ì', 'í', 'î', 'ï' -> 'i'
                    'ñ' -> 'n'
                    'ò', 'ó', 'ô', 'ö', 'õ' -> 'o'
                    'ù', 'ú', 'û', 'ü' -> 'u'
                    'ý', 'ÿ' -> 'y'
                    else -> char
                }
            }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
}

private fun String.leadingChannelNumber(): String? {
    val trimmed = trimStart()
    val digits = trimmed.takeWhile { it.isDigit() }
    if (digits.isBlank()) return null
    val separator = trimmed.getOrNull(digits.length)
    return digits.takeIf { separator == ' ' || separator == '-' || separator == '.' || separator == ':' }
}

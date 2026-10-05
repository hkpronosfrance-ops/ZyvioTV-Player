package fr.zyviotv.player.shared.catalog

data class CatalogCategory(
    val id: String,
    val name: String,
)

data class CatalogLiveChannel(
    val id: String,
    val name: String,
    val categoryId: String?,
    val logoUrl: String?,
    val streamUrl: String,
)

data class CatalogMovie(
    val id: String,
    val title: String,
    val categoryId: String?,
    val posterUrl: String?,
    val streamUrl: String,
    val containerExtension: String,
)

data class CatalogSeries(
    val id: String,
    val title: String,
    val categoryId: String?,
    val posterUrl: String?,
)

data class CatalogSnapshot(
    val liveCategories: List<CatalogCategory> = emptyList(),
    val liveChannels: List<CatalogLiveChannel> = emptyList(),
    val movieCategories: List<CatalogCategory> = emptyList(),
    val movies: List<CatalogMovie> = emptyList(),
    val seriesCategories: List<CatalogCategory> = emptyList(),
    val series: List<CatalogSeries> = emptyList(),
)

sealed interface CatalogLoadResult {
    data class Success(val snapshot: CatalogSnapshot) : CatalogLoadResult
    data class Failure(val message: String) : CatalogLoadResult
}

interface CatalogRepository {
    suspend fun load(): CatalogLoadResult
}

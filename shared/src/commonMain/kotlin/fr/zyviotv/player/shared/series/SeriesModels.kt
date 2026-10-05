package fr.zyviotv.player.shared.series

import fr.zyviotv.player.shared.security.SecretLoggingPolicy

data class SeriesSummary(
    val id: String,
    val title: String,
    val year: Int? = null,
    val rating: Double? = null,
    val posterUrl: String? = null,
)

data class SeriesEpisode(
    val id: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val synopsis: String? = null,
    val durationMinutes: Int? = null,
    val streamUrl: String,
    val resumePositionMs: Long = 0L,
    val watched: Boolean = false,
) {
    override fun toString(): String =
        "SeriesEpisode(id=$id, seasonNumber=$seasonNumber, episodeNumber=$episodeNumber, title=$title, durationMinutes=$durationMinutes, streamUrl=${SecretLoggingPolicy.redactUrlForLogs(streamUrl)}, resumePositionMs=$resumePositionMs, watched=$watched)"
}

data class SeriesSeason(
    val number: Int,
    val episodes: List<SeriesEpisode>,
)

data class SeriesDetails(
    val id: String,
    val title: String,
    val synopsis: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val seasons: List<SeriesSeason> = emptyList(),
)

object SeriesNavigator {
    fun nextEpisode(
        seasons: List<SeriesSeason>,
        currentEpisodeId: String?,
    ): SeriesEpisode? {
        val ordered = seasons
            .sortedBy { it.number }
            .flatMap { season -> season.episodes.sortedBy { it.episodeNumber } }

        if (ordered.isEmpty()) return null
        if (currentEpisodeId == null) return ordered.firstOrNull { !it.watched } ?: ordered.first()

        val index = ordered.indexOfFirst { it.id == currentEpisodeId }
        if (index < 0) return ordered.firstOrNull { !it.watched } ?: ordered.first()
        return ordered.getOrNull(index + 1)
    }
}

sealed interface EpisodeValidationResult {
    data object Valid : EpisodeValidationResult
    data class Invalid(val message: String) : EpisodeValidationResult
}

object EpisodeValidator {
    fun validate(episode: SeriesEpisode): EpisodeValidationResult {
        if (episode.seasonNumber < 0 || episode.episodeNumber <= 0) {
            return EpisodeValidationResult.Invalid("Numéro de saison ou d’épisode invalide.")
        }
        if (episode.title.isBlank()) {
            return EpisodeValidationResult.Invalid("Le titre de l’épisode est manquant.")
        }
        if (!episode.streamUrl.startsWith("http://") && !episode.streamUrl.startsWith("https://")) {
            return EpisodeValidationResult.Invalid("Le flux de l’épisode est invalide.")
        }
        if (episode.resumePositionMs < 0L) {
            return EpisodeValidationResult.Invalid("La position de reprise est invalide.")
        }
        return EpisodeValidationResult.Valid
    }
}

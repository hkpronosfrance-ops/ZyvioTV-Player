package fr.zyviotv.player.shared.movies

import fr.zyviotv.player.shared.security.SecretLoggingPolicy

data class MovieSummary(
    val id: String,
    val title: String,
    val year: Int? = null,
    val rating: Double? = null,
    val posterUrl: String? = null,
)

data class MovieDetails(
    val id: String,
    val title: String,
    val synopsis: String? = null,
    val year: Int? = null,
    val durationMinutes: Int? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val streamUrl: String,
    val resumePositionMs: Long = 0L,
) {
    override fun toString(): String =
        "MovieDetails(id=$id, title=$title, year=$year, durationMinutes=$durationMinutes, rating=$rating, genres=$genres, posterUrl=$posterUrl, backdropUrl=$backdropUrl, streamUrl=${SecretLoggingPolicy.redactUrlForLogs(streamUrl)}, resumePositionMs=$resumePositionMs)"
}

sealed interface MovieValidationResult {
    data object Valid : MovieValidationResult
    data class Invalid(val message: String) : MovieValidationResult
}

object MovieValidator {
    fun validate(details: MovieDetails): MovieValidationResult {
        if (details.title.isBlank()) return MovieValidationResult.Invalid("Le titre du film est manquant.")
        if (!details.streamUrl.startsWith("http://") && !details.streamUrl.startsWith("https://")) {
            return MovieValidationResult.Invalid("Le flux du film est invalide.")
        }
        if (details.resumePositionMs < 0L) return MovieValidationResult.Invalid("La position de reprise est invalide.")
        return MovieValidationResult.Valid
    }
}

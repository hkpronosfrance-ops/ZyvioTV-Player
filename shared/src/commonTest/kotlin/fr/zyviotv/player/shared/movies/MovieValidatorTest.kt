package fr.zyviotv.player.shared.movies

import kotlin.test.Test
import kotlin.test.assertIs

class MovieValidatorTest {
    @Test
    fun validMoviePasses() {
        assertIs<MovieValidationResult.Valid>(
            MovieValidator.validate(
                MovieDetails(
                    id = "1",
                    title = "Film",
                    streamUrl = "https://stream.example/movie.mp4",
                ),
            ),
        )
    }

    @Test
    fun invalidStreamFails() {
        assertIs<MovieValidationResult.Invalid>(
            MovieValidator.validate(
                MovieDetails(
                    id = "1",
                    title = "Film",
                    streamUrl = "file:///movie.mp4",
                ),
            ),
        )
    }
}

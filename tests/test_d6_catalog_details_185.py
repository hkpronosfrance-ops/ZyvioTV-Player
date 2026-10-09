"""D6 media detail integration, real images and actual ordered series episodes."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/fr/zyviotv/player/ui"

def test_android_media_hero_displays_image_when_available():
    hero = (UI / "catalog/D6MediaDetailHero.kt").read_text()
    movie = (UI / "movies/MovieDetailScreen.kt").read_text()
    assert "artworkUrl: String? = null" in hero
    assert "AsyncImage(" in hero and "ContentScale.Crop" in hero
    assert "if (!artworkUrl.isNullOrBlank())" in hero
    assert "artworkUrl = movie.backdropUrl ?: movie.posterUrl" in movie
    assert "ZyvioSurface1.copy(alpha = 0.94f)" in hero

def test_android_series_preserves_genuine_episode_order():
    screen = (UI / "series/SeriesDetailScreen.kt").read_text()
    assert "sortedBy { it.number }" in screen
    assert "series.episodes.sortedWith(compareBy({ it.season }, { it.number }))" in screen

def test_ios_series_defaults_to_earliest_real_season_and_sorts_episode_numbers():
    screen = (ROOT / "iosApp/SeriesView.swift").read_text()
    assert "selectedSeason = selectedSeason ?? loadedDetail.episodes.map(\\.season).min()" in screen
    assert ".sorted { $0.number < $1.number }" in screen

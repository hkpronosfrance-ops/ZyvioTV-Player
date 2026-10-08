"""Claude D6 cohesive media detail, episodes and player UI contracts."""
from pathlib import Path

UI = Path(__file__).resolve().parents[1] / "app/src/main/java/fr/zyviotv/player/ui"


def test_shared_details_hero_is_used_by_movie_and_series():
    hero = (UI / "catalog/D6MediaDetailHero.kt").read_text()
    assert "Brush.verticalGradient" in hero
    assert "ZyvioRedTint" in hero
    assert "text = title" in hero
    for path in ("movies/MovieDetailScreen.kt", "series/SeriesDetailScreen.kt"):
        source = (UI / path).read_text()
        assert "D6MediaDetailHero(" in source, path


def test_series_primary_play_respects_episode_chronology_and_loading():
    source = (UI / "series/SeriesDetailScreen.kt").read_text()
    assert "sortedWith(compareBy({ it.season }, { it.number }))" in source
    assert "LaunchedEffect(seasons, selectedSeason)" in source
    assert "selectedSeason = seasons.first()" in source


def test_video_resume_and_channel_overlays_use_d6_spacing():
    source = (UI / "player/PlayerScreen.kt").read_text()
    assert source.count("Column(Modifier.padding(ZyvioSpace.s6))") >= 2
    assert "Column(Modifier.padding(ZyvioSpace.s5))" in source
    assert "Color(0xFF151515)" not in source

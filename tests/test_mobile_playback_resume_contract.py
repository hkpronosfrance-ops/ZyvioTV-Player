"""Source-level regression guard for mobile playback resume and next episode."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_apple_completed_items_restart():
    for name in ("MoviesView.swift", "SeriesView.swift"):
        source = (ROOT / "iosApp" / name).read_text()
        assert "guard let progress = existingProgress, !progress.completed else { return 0 }" in source
        assert "Double(milliseconds) / Double(duration) >= 0.95 { return 0 }" in source


def test_android_next_episode_uses_season_episode_order():
    source = (ROOT / "app/src/main/java/fr/zyviotv/player/ui/player/SeriesAutoNextResolver.kt").read_text()
    assert "detail.episodes.sortedWith(" in source
    assert "compareBy<SeriesEpisodeSource> { it.season }.thenBy { it.number }" in source

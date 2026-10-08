"""Cross-platform next-episode source contract checks."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_apple_next_episode_chronological():
    source = (ROOT / "iosApp/SeriesView.swift").read_text()
    assert "let ordered = allEpisodes.sorted" in source
    assert "$0.season < $1.season" in source
    assert "$0.number < $1.number" in source
    assert "ordered.firstIndex(where: { $0.id == episode.id })" in source


def test_android_episode_fallback_requires_known_numbers():
    source = (ROOT / "app/src/main/java/fr/zyviotv/player/ui/player/SeriesAutoNextResolver.kt").read_text()
    assert "currentSeason != null" in source
    assert "currentEpisode != null" in source

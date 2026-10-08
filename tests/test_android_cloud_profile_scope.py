"""Regression checks for Android account + profile scoped cloud library access."""
from pathlib import Path

SOURCE = (Path(__file__).resolve().parents[1] / "app/src/main/java/fr/zyviotv/player/data/sync/SupabaseLibrarySyncRepository.kt").read_text()


def method(name):
    start = SOURCE.index("fun " + name + "(")
    return SOURCE[start:SOURCE.index("\n    }", start) + 6]


def test_favorites_and_progress_reads_and_deletions_scoped_by_user():
    for name in ("listFavorites", "listWatchProgress", "removeFavorite", "removeWatchProgress", "listLiveHistory"):
        code = method(name)
        assert "fetchCurrentUserId(session.accessToken)" in code, name
        assert '"?user_id=eq." + encoded(userId) + "&profile_id=eq." + encoded(profileId)' in code, name


def test_mutations_preserve_playlist_content_identity():
    for name in ("removeFavorite", "removeWatchProgress"):
        code = method(name)
        for field in ('"&playlist_id=eq."', '"&content_type=eq."', '"&content_id=eq."'):
            assert field in code, (name, field)

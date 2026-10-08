"""Apple cloud sync profile isolation contract regression guards."""
from pathlib import Path

SOURCE = (Path(__file__).resolve().parents[1] / "iosApp/SupabaseLibrarySyncService.swift").read_text()


def method_source(name):
    start = SOURCE.index("    func " + name)
    return SOURCE[start:SOURCE.index("\n    }", start) + 6]


def test_all_favorites_and_history_queries_are_user_profile_scoped():
    for method in ("listFavorites()", "listWatchProgress(limit:", "removeFavorite(", "removeWatchProgress("):
        body = method_source(method)
        assert "let profileId = try activeProfileId()" in body
        assert "let userId = try await currentUserId()" in body
        assert "user_id=eq.\\(encoded(userId))&profile_id=eq.\\(encoded(profileId))" in body


def test_write_operations_pin_profile_before_remote_await():
    for method in ("upsertFavorite(", "upsertWatchProgress("):
        body = method_source(method)
        assert body.index("let profileId = try activeProfileId()") < body.index("let userId = try await currentUserId()")

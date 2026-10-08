"""Android library asynchronous state race contract checks."""
from pathlib import Path

SOURCE = (Path(__file__).resolve().parents[1] / "app/src/main/java/fr/zyviotv/player/ui/library/LibrarySession.kt").read_text()


def test_delayed_cloud_writes_cannot_restore_an_old_profile():
    for method in ("toggleFavorite", "saveProgress", "recordLiveHistory", "removeProgress"):
        section = SOURCE.split("    suspend fun " + method + "(", 1)[1].split("\n    }", 1)[0]
        assert "(state.value as? LibraryState.Ready)?.snapshot?.profileId == ready.snapshot.profileId" in section, method
        assert "val current = state.value as LibraryState.Ready" in section, method
        assert "updateState(current.copy(" in section, method


def test_content_mutations_keep_composite_content_identity():
    for field in ("playlistId", "contentType", "contentId"):
        assert field in SOURCE

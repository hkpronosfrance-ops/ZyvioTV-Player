"""Protect TV cloud-library queries from cross-user/profile regressions."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_tv_library_list_and_delete_scope():
    for platform in ("tizenApp", "webosApp"):
        source = (ROOT / platform / "cloud.js").read_text()
        for name in ("listFavorites", "removeFavorite", "listWatchProgress"):
            marker = "  async function " + name + "("
            part = source.split(marker, 1)[1].split("\n  }", 1)[0]
            assert "const user = await currentUser(session);" in part, (platform, name)
            assert '"?user_id=eq." + encodeURIComponent(user.id)' in part, (platform, name)
            assert '"&profile_id=eq." + encodeURIComponent(profileId)' in part, (platform, name)

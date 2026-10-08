"""Claude D6 discovery UI contracts: home, search, film and series catalogs."""
from pathlib import Path

UI = Path(__file__).resolve().parents[1] / "app/src/main/java/fr/zyviotv/player/ui"


def test_search_filters_show_selection_and_scroll_without_clipping():
    s = (UI / "search/SearchScreen.kt").read_text()
    assert "FilterChip(" in s
    assert "selected = filter == item" in s
    assert "horizontalScroll(rememberScrollState())" in s
    assert "Modifier.tvFocusEffect(profile == DeviceProfile.Television" in s
    assert "AssistChip(" not in s


def test_home_hero_is_branded_and_mobile_text_stays_readable():
    s = (UI / "home/HomeScreen.kt").read_text()
    assert "ZyvioRedTint.copy(alpha = 0.96f)" in s
    assert "ZyvioBase.copy(alpha = 0.88f)" in s
    assert ".fillMaxWidth(if (profile == DeviceProfile.Mobile) 0.94f else 0.78f)" in s
    assert "ZyvioSurface2," in s and "ZyvioSurface1," in s


def test_both_catalogs_share_consistent_grid_spacing_and_poster_ratio():
    for path in ("movies/MoviesScreen.kt", "series/SeriesScreen.kt"):
        s = (UI / path).read_text()
        assert "GridCells.Adaptive(minSize = posterMinWidth)" in s, path
        assert "Arrangement.spacedBy(ZyvioSpace.s4)" in s, path
        assert "ZyvioSpace.s2 else ZyvioSpace.s3" in s, path
        assert ".aspectRatio(2f / 3f)" in s, path
        assert ".tvFocusEffect(" in s, path

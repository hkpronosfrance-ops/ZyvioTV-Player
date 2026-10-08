"""Responsive film/series grid design contracts for Android D6."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/java/fr/zyviotv/player/ui"


def test_shared_poster_widths_per_device_profile():
    source = (ROOT / "catalog/CatalogGridSizing.kt").read_text()
    assert "DeviceProfile.Mobile -> 128.dp" in source
    assert "DeviceProfile.Tablet -> 168.dp" in source
    assert "DeviceProfile.Television -> 228.dp" in source


def test_movie_and_series_ready_and_skeleton_grids_use_adaptive_width():
    for relative in ("movies/MoviesScreen.kt", "series/SeriesScreen.kt"):
        source = (ROOT / relative).read_text()
        assert source.count("GridCells.Adaptive(minSize = posterMinWidth)") == 2, relative
        assert source.count("val posterMinWidth = catalogPosterMinimumWidth(profile)") == 2, relative
        assert "GridCells.Fixed(columns)" not in source, relative
        assert ".aspectRatio(2f / 3f)" in source, relative


def test_tv_focus_restoration_survives_grid_resizing():
    for relative in ("movies/MoviesScreen.kt", "series/SeriesScreen.kt"):
        source = (ROOT / relative).read_text()
        assert "gridState.scrollToItem(index)" in source, relative
        assert "focusRequesters[lastSelectedId]?.requestFocus()" in source, relative

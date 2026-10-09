"""D6 responsive layouts match Apple and both television platforms."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def test_apple_movie_and_series_grids_adapt_to_available_width():
    for p in ("iosApp/MoviesView.swift", "iosApp/SeriesView.swift"):
        s=(ROOT / p).read_text()
        assert "GridItem(.adaptive(minimum: minimumPosterWidth)" in s, p
        assert "horizontalSizeClass == .regular ? 168 : 132" in s, p
        assert "GridItem(.flexible()" not in s, p
        assert "ZyvioDesign.Space.s7" in s, p

def test_samsung_and_lg_tv_shelves_respond_without_clipping():
    for p in ("tizenApp/styles.css", "webosApp/styles.css"):
        s=(ROOT / p).read_text()
        assert "grid-template-columns: repeat(auto-fit, minmax(min(100%, 176px), 1fr))" in s, p
        assert "overflow-x: auto" in s, p
        assert "@media (max-width: 1280px)" in s, p
        assert "@media (min-width: 2560px)" in s, p
        assert "outline: 4px solid var(--zyvio-focus-ring)" in s, p
        assert "prefers-reduced-motion: reduce" in s, p

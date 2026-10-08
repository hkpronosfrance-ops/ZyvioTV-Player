"""D6 design adoption checks for real Apple Live / VOD screens."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "iosApp"


def test_live_movies_series_use_d6_surfaces():
    for name in ("LiveTvView.swift", "MoviesView.swift", "SeriesView.swift"):
        source = (ROOT / name).read_text()
        assert "ZyvioDesign.Palette.base" in source, name
        assert "Color.black.ignoresSafeArea()" not in source, name
        assert ".background(Color.black)" not in source, name


def test_live_vod_screen_spacing_uses_d6_grid():
    for name in ("LiveTvView.swift", "MoviesView.swift", "SeriesView.swift"):
        source = (ROOT / name).read_text()
        assert "ZyvioDesign.Space." in source, name
        assert ".padding(16)" not in source, name
        assert ".padding(24)" not in source, name

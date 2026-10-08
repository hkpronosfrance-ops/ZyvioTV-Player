"""Claude D6 adoption guards for Apple core UI screens."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "iosApp"


def test_apple_core_screens_use_d6_base_surfaces():
    for name in ("HomeView.swift", "MainTabView.swift", "RootView.swift", "AuthView.swift"):
        source = (ROOT / name).read_text()
        assert "ZyvioDesign.Palette.base" in source, name
        assert "Color.black.ignoresSafeArea()" not in source, name
        assert ".background(Color.black)" not in source, name


def test_main_navigation_and_login_follow_d6_spacing():
    for name in ("MainTabView.swift", "RootView.swift", "AuthView.swift"):
        source = (ROOT / name).read_text()
        assert "ZyvioDesign.Space.s6" in source, name
        assert ".padding(24)" not in source, name

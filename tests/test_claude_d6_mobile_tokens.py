"""Claude D6 Android and Apple mobile design token parity."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = (ROOT / "app/src/main/java/fr/zyviotv/player/ui/theme/ZyvioDesignTokens.kt").read_text()
APPLE = (ROOT / "iosApp/ZyvioDesignTokens.swift").read_text()


def test_spacing_grid_agrees_between_android_and_apple():
    steps = {"s1": 4, "s2": 8, "s3": 12, "s4": 16, "s5": 20,
             "s6": 24, "s8": 32, "s10": 40, "s12": 48, "s16": 64, "s24": 96}
    for name, value in steps.items():
        assert f"val {name} = {value}.dp" in ANDROID
        assert f"let {name}: CGFloat = {value}" in APPLE


def test_motion_and_radius_tokens_follow_d6():
    assert "pressMillis = 90" in ANDROID
    assert "focusMillis = 150" in ANDROID
    assert "playerControlsHideTvMillis = 5000" in ANDROID
    assert "let focus: Double = 0.15" in APPLE
    assert "let xl = 24.dp" in ANDROID
    assert "let xl: CGFloat = 24" in APPLE

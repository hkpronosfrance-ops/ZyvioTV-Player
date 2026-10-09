"""Claude D6 Android TV navigation/focus and screen layout contracts."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"app/src/main/java/fr/zyviotv/player/ui"
def test_tv_focus_white_outline_and_subtle_scale():
    s=(ROOT/"tv/TvFocus.kt").read_text()
    assert "1.02f else 1f" in s
    assert "4.dp else 0.dp" in s
    assert "ZyvioTextPrimary" in s
    assert ".onFocusChanged { focused = it.isFocused }" in s
def test_tv_home_hero_and_navigation_are_designed_for_10ft():
    s=(ROOT/"home/HomeScreen.kt").read_text()
    assert "DeviceProfile.Television -> 390.dp" in s
    assert "DeviceProfile.Television -> 220.dp" in s
    assert "DeviceProfile.Television -> ZyvioSpace.s12" in s
    assert ".tvFocusEffect(isTelevision" in s
def test_tv_live_two_panel_scales_to_actual_tv_width():
    s=(ROOT/"live/LiveTvScreen.kt").read_text()
    assert "(availableWidth * 0.30f).coerceAtMost(440.dp)" in s
    assert "Arrangement.spacedBy(if (isTelevision) 28.dp else 18.dp)" in s
    assert "onTuneChannel = onTuneChannel" in s

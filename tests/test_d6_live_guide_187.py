"""D6 live and guide responsive + real EPG ordering contracts."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def test_ios_selected_channel_and_category_state():
    s=(ROOT/"iosApp/LiveTvView.swift").read_text()
    assert "LazyHStack(spacing: ZyvioDesign.Space.s2)" in s
    assert "ZyvioDesign.Palette.brand.opacity(0.20)" in s
    assert "ZyvioDesign.Palette.surface1" in s
    assert "selectedCategory = id" in s
    assert "playing = channel" in s
def test_ios_guide_next_programme_does_not_depend_on_provider_row_order():
    s=(ROOT/"iosApp/LiveTvView.swift").read_text()
    assert ".filter { $0.startEpochSeconds >= threshold }" in s
    assert ".min { $0.startEpochSeconds < $1.startEpochSeconds }" in s
    assert "time >= $0.startEpochSeconds && time < $0.endEpochSeconds" in s
def test_android_live_sidebar_responsive_on_tablet_and_tv():
    s=(ROOT/"app/src/main/java/fr/zyviotv/player/ui/live/LiveTvScreen.kt").read_text()
    assert "LocalConfiguration.current.screenWidthDp.dp" in s
    assert "(availableWidth * 0.32f).coerceAtMost(420.dp)" in s
    assert "(availableWidth * 0.40f).coerceAtMost(330.dp)" in s
    assert ".width(channelListWidth)" in s
    assert "onTuneChannel = onTuneChannel" in s

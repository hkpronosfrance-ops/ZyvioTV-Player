"""D6 Live TV and EPG visual contracts, preserving tune and remote focus behavior."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/java/fr/zyviotv/player/ui"


def test_live_preview_uses_branded_fallback_and_stable_controls():
    source = (ROOT / "live/LiveTvScreen.kt").read_text()
    assert "Brush.verticalGradient(listOf(ZyvioSurface2, ZyvioRedTint, ZyvioSurface1))" in source
    assert "Column(Modifier.padding(ZyvioSpace.s5))" in source
    assert "onTuneChannel(it)" in source
    assert "modifier = Modifier.tvFocusEffect(" in source


def test_epg_current_program_distinct_from_past_and_future():
    source = (ROOT / "epg/GuideEpgScreen.kt").read_text()
    assert "isCurrent -> ZyvioRedTint" in source
    assert "isPast -> ZyvioSurface1" in source
    assert "else -> ZyvioSurface2" in source
    assert "Column(Modifier.padding(ZyvioSpace.s5))" in source


def test_tv_focus_and_timeline_scroll_are_preserved():
    source = (ROOT / "epg/GuideEpgScreen.kt").read_text()
    assert "tvFocusEffect(isTelevision, cornerRadiusDp = 8)" in source
    assert "epgFocusKey(" in source
    assert ".horizontalScroll(horizontal)" in source

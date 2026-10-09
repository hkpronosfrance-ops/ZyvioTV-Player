"""D6 Home discovery contract on Apple and Android form factors."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def test_ios_home_hero_shows_real_artwork_and_readable_gradient():
    s=(ROOT/"iosApp/HomeView.swift").read_text()
    assert "if let artwork = item?.artworkUrl, let url = URL(string: artwork)" in s
    assert "AsyncImage(url: url)" in s
    assert "ZyvioDesign.Palette.base.opacity(0.95)" in s
    assert "frame(height: isWide ? 330 : 252)" in s

def test_ios_shelves_have_lazy_layout_and_shared_spacing():
    s=(ROOT/"iosApp/HomeView.swift").read_text()
    assert "LazyHStack(alignment: .top, spacing: ZyvioDesign.Space.s3)" in s
    assert "ZyvioDesign.Space.s8 : ZyvioDesign.Space.s5" in s
    assert "ProgressShelf(" in s and "FavoriteShelf(" in s

def test_android_discovery_actions_have_device_specific_widths():
    s=(ROOT/"app/src/main/java/fr/zyviotv/player/ui/home/HomeScreen.kt").read_text()
    for case in ("DeviceProfile.Mobile -> 150.dp","DeviceProfile.Tablet -> 176.dp","DeviceProfile.Television -> 190.dp"):
        assert case in s
    assert "QuickActionCard(" in s and ".width(cardWidth)" in s
    assert ".tvFocusEffect(isTelevision" in s

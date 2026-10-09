#!/usr/bin/env python3
"""Deterministic source QA for ZYVIOTV D7. Does not certify screenshots or devices."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TV = ("tizenApp", "webosApp")
REQUIRED_IDS = (
    "auth-screen", "profile-screen", "pin-screen", "system-screen",
    "app-shell", "tv-player", "continue-shelf", "next-episodes-shelf",
    "recent-channels-shelf", "favorites-shelf", "catalog-panel",
    "catalog-grid", "devices-panel", "status",
)
REQUIRED_TV_CSS = (
    "--zyvio-tv-safe: 32px", "--zyvio-tv-safe: 20px",
    "--zyvio-tv-safe: 48px", "--zyvio-focus-ring: #F4F4F6",
    "outline: 4px solid var(--zyvio-focus-ring)",
    "prefers-reduced-motion: reduce", "grid-template-columns: repeat(auto-fit",
)
APPLE = ("MoviesView.swift", "SeriesView.swift")
ANDROID = (
    "home/HomeScreen.kt", "search/SearchScreen.kt",
    "live/LiveTvScreen.kt", "epg/GuideEpgScreen.kt",
    "movies/MovieDetailScreen.kt", "series/SeriesDetailScreen.kt",
)
def audit(root=ROOT):
    checks = []
    def expect(platform, check, ok):
        checks.append({"platform": platform, "check": check, "passed": bool(ok)})
    for platform in TV:
        html = (root / platform / "index.html").read_text()
        css = (root / platform / "styles.css").read_text()
        for identifier in REQUIRED_IDS:
            expect(platform, "route: " + identifier, 'id="' + identifier + '"' in html)
        for token in REQUIRED_TV_CSS:
            expect(platform, "style: " + token, token in css)
        expect(platform, "focusable controls", "data-focusable" in html)
        expect(platform, "safe catalogue layout", "overflow-x: auto" in css)
    for filename in APPLE:
        code = (root / "iosApp" / filename).read_text()
        expect("iOS", filename + " adaptive posters", "GridItem(.adaptive(" in code)
        expect("iOS", filename + " D6 spacing", "ZyvioDesign.Space.s" in code)
    for filename in ANDROID:
        code = (root / "app/src/main/java/fr/zyviotv/player/ui" / filename).read_text()
        expect("Android", filename + " composable", "@Composable" in code)
    return checks

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    checks = audit()
    passed = sum(c["passed"] for c in checks)
    result = {"passed": passed, "total": len(checks), "failed": [c for c in checks if not c["passed"]], "device_qa": "NOT EXECUTED", "visual_parity": "NOT CERTIFIED"}
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(f"D7 static release audit: {passed}/{len(checks)} passed")
        for failure in result["failed"]:
            print(f"FAIL {failure['platform']}: {failure['check']}")
        print("Visual parity: NOT CERTIFIED; physical device QA: NOT EXECUTED")
    return 0 if passed == len(checks) else 1

if __name__ == "__main__":
    raise SystemExit(main())

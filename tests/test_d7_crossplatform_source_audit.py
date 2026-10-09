"""Executable D7 cross-platform release audit regression tests."""
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("d7_source_audit", ROOT / "scripts/d7_source_audit.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

def test_all_source_audit_checks_pass():
    failures = [c for c in module.audit() if not c["passed"]]
    assert not failures, failures

def test_audit_detects_missing_screen_or_focus(tmp_path):
    for platform in module.TV:
        target = tmp_path / platform
        target.mkdir()
        (target / "index.html").write_text("<html><body>missing D7 routes</body></html>")
        (target / "styles.css").write_text("/* no focus styles */")
    (tmp_path / "iosApp").mkdir()
    for name in module.APPLE:
        (tmp_path / "iosApp" / name).write_text("struct View {}")
    android = tmp_path / "app/src/main/java/fr/zyviotv/player/ui"
    for name in module.ANDROID:
        path = android / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("package fake")
    results = module.audit(tmp_path)
    assert results and any(not item["passed"] for item in results)
    assert any(item["check"] == "route: auth-screen" and not item["passed"] for item in results)

def test_qa_report_never_claims_actual_visual_signoff():
    source = (ROOT / "scripts/d7_source_audit.py").read_text()
    assert "NOT CERTIFIED" in source
    assert "NOT EXECUTED" in source

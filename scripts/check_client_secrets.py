#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOTS = [
    Path("app/src"),
    Path("iosApp"),
    Path("shared/src/commonMain"),
    Path("tizenApp"),
    Path("webosApp"),
]

TEXT_SUFFIXES = {".kt", ".kts", ".swift", ".js", ".cjs", ".html", ".css", ".json", ".xml", ".md"}
PATTERNS = [
    ("privileged Supabase key marker", re.compile(r"sb_secret_|service_role", re.I)),
    ("Xtream URL credential fixture", re.compile(r"/(?:live|movie|series)/[^/\s]+/[^/\s]+/\d+", re.I)),
    ("M3U URL credential query", re.compile(r"[?&](?:username|user)=[^&\s]+&(?:password|pass)=[^&\s]+", re.I)),
]

ALLOW_MARKERS = (
    "[REDACTED]",
    "example.com",
    "provider.example",
)

violations = []

for root in ROOTS:
    if not root.exists():
        continue
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue

        for line_no, line in enumerate(text.splitlines(), 1):
            if any(marker in line for marker in ALLOW_MARKERS):
                continue
            for label, pattern in PATTERNS:
                if pattern.search(line):
                    violations.append((path, line_no, label))

if violations:
    for path, line_no, label in violations:
        print(f"{path}:{line_no}: {label}")
    print("Potential sensitive provider credential material found.")
    sys.exit(1)

print("Client credential fixture scan OK")

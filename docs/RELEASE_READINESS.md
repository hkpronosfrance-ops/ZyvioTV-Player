# ZYVIOTV Player — release readiness

## Automated baseline

A release candidate must pass all applicable GitHub workflows on the exact candidate commit:

- Shared + Android CI
- Android Smoke APK
- iOS CI
- Release Gate
- Tizen CI when TV client files change
- webOS CI when TV client files change

The Release Gate additionally verifies:

- Android unit tests
- release lint
- minified release APK build
- TV JavaScript syntax
- TV provider parser/redaction regression tests
- TV playback adapter syntax
- TV manifest structure
- absence of privileged Supabase client secret markers
- absence of obvious committed IPTV credential fixtures

## Production publication prerequisites

These are intentionally **not automated or claimed complete**:

- Android release signing and Play Console configuration
- Apple signing/provisioning and App Store Connect configuration
- Samsung certificate/profile and Seller Office package validation
- LG signing/package and Seller Lounge validation
- physical-device playback matrix
- real authorized provider compatibility checks
- privacy policy/store metadata/screenshots/review text
- final legal/product review for distribution territories

## Supported-claim rule

A platform can only be advertised as production-supported after:

1. automated CI is green,
2. a signed installable candidate exists,
3. the physical QA rows for that platform are complete,
4. at least one authorized real-provider playback test passes,
5. critical/high defects are closed.

Until then, use wording such as **development support**, **preview**, or **validation pending**.

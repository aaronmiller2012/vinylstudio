# VinylStudio

**Current build:** versionName 3.7.0, versionCode 569, APK `VinylHalftoneStudio-v3.7.0-Product569.apk`.

VinylStudio is an Android app that turns photos into stacked, opaque vinyl cut sheets. Each color becomes one physical layer, exported as per-color SVG/DXF cut files (plus PNG/PDF artwork and an assembly guide) for cutting machines such as the Cricut Explore Air 2 on a 12 × 12 in mat.

> **Status:** build 569 (3.7.0) release candidate: host-gated (91 gates), unit-tested and measured on an Android tablet (all 114 styles render; device regression suite passes). It has not been qualified in Cricut Design Space, Silhouette Studio or on a physical cutter. The debug APK is a development build.

## Features

- Import from the photo picker, Share or "Open with"; crop and Snip (polygon cut-out).
- 100+ style tiles, including eight portrait maze styles with a verified single solution: CMYK/spot halftones, line art, engraving, linocut, tessellations, layered cells, Prismatic ribbons, organic flow and more.
- Photo-derived palettes (OKLab analysis) mapped to physical vinyl colors.
- Cut-safety preflight against the material's minimum cut width and weed gap; unverified sheets need explicit review.
- Verified cut ZIP export (SVG and DXF R13), registration marks, and mat-fit checks that refuse exports that do not fit.
- No network access, no analytics, no accounts. Projects stay on the device.

## Requirements

- JDK 17+ (CI uses 21)
- Android SDK with platform 36 (`ANDROID_HOME` set)
- Python 3 for the gate scripts

## Build

```bash
./gradlew --no-daemon :app:assembleDebug
```

Release builds (`assembleRelease` / `bundleRelease`) are deliberately fail-closed. They need a public `VINYL_PRIVACY_URL` and the `VINYL_RELEASE_*` signing variables.

## Test

```bash
python3 -I tests/check-gate-manifest.py              # gate list consistency
bash tests/run-v3-host-gate.sh                       # one host gate
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug
```

The full list of mandatory gates is in `tests/gates.manifest`, and CI (`.github/workflows/verify.yml`) runs all of them. Host gates compile engines directly against JTS 1.20.0. Set `VINYL_JTS_JAR`, or let the scripts find it in the Gradle cache.

## Documentation

- `docs/ARCHITECTURE.md`: code map
- `docs/RENDER_PIPELINE.md`: render pipeline
- `docs/CUT_SAFETY.md`: cut-safety rules
- `docs/QUALITY_GATES.md`: test gates
- `docs/ROADMAP.md`: plan
- `docs/DECISIONS/`: architecture decision records
- `docs/RELEASE-NOTES.md`: historical per-build notes
- `CHANGELOG.md`

## Third-party

Uses the LocationTech JTS Topology Suite 1.20.0 (EPL-2.0 / EDL-1.0). See `legal/JTS-NOTICE.md`.

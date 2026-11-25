#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2520-gate.sh
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TMP" app/src/main/java/com/halftonestudio/{SnipMask,RegistrationMarks,CmykWhitePaperBalance,CmykPortraitDefaults,LinePresenceTracker,PortraitBackgroundGuard,LineArtEngine,StencilSeparationEngine,VinylEngine,AsciiScreenMotif,AsciiVectorGlyphs,StyleKit,LineCutDiagnostics,ParallelGeometry,CancellationCheckpoint}.java tests/LineV2521HighlightRetentionTest.java
java -Xmx512m -cp "$TMP" com.halftonestudio.LineV2521HighlightRetentionTest
python3 tests/check-v2521-line-visibility.py
printf 'PASS v2.52.1 line visibility release gate\n'

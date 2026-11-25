#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TMP" app/src/main/java/com/halftonestudio/{SnipMask,RegistrationMarks,CmykWhitePaperBalance,CmykPortraitDefaults,LinePresenceTracker,PortraitBackgroundGuard,LineRibbonPolicy,LineArtEngine,StencilSeparationEngine,VinylEngine,AsciiScreenMotif,AsciiVectorGlyphs,StyleKit,LineCutDiagnostics,ParallelGeometry,CancellationCheckpoint}.java tests/{LineArtEngineTest,LineCutReleaseTest,LineV2494SignatureFixture,LineV2503RegressionTest,LineV2503DiagnosticsTest}.java
java -Xmx512m -cp "$TMP" com.halftonestudio.LineArtEngineTest
java -Xmx512m -cp "$TMP" com.halftonestudio.LineCutReleaseTest
java -Xmx512m -cp "$TMP" com.halftonestudio.LineV2503RegressionTest
java -Xmx512m -cp "$TMP" com.halftonestudio.LineV2503DiagnosticsTest
python3 tests/check-v2503-cut-geometry.py

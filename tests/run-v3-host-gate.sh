#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
# Keep every inherited executable geometry, export, library and lifecycle gate.
bash tests/run-rc173-w5-host-gate.sh
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx700m -d "$TMP" \
  app/src/main/java/com/halftonestudio/{ProjectArchiveReader,ProjectImageSignature,JsonSyntaxVerifier,ProjectEncodedOriginal,ProjectImageFileCompare,ProjectTempJanitor,ProjectManifest,EngineBuild}.java \
  tests/V3ProjectStreamingTest.java
java -Xmx256m -ea -cp "$TMP" com.halftonestudio.V3ProjectStreamingTest
python3 tests/check-v3-integration.py
# Beta 2 regression: real Java execution of ZIP role policy, temp-file
# cleanup, rotated narrow cuts, and explicit export-severity decisions.
javac -J-Xmx700m -sourcepath app/src/main/java -d "$TMP" \
  app/src/main/java/com/halftonestudio/{StyleKit,StyleCutGeometryPreflight,CutSafetyReviewPolicy,ProjectArchiveReader,StudioProjectLibrary,ProjectManifest,EngineBuild,SharedExportFiles,ParallelGeometry,CancellationCheckpoint,FastGeom}.java \
  tests/V3Beta2SafetyTest.java
java -Xmx512m -ea -cp "$TMP" com.halftonestudio.V3Beta2SafetyTest
python3 tests/check-v3-beta2-integration.py
printf '\nPASS VinylStudio v3 Beta2 inherited + archive/geometry/export safety gates\n'


# Beta 3 executable adversarial gate (including injected photo-write failure).
BETA3_TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP" "$BETA3_TMP"' EXIT
javac -J-Xmx700m -sourcepath app/src/main/java -d "$BETA3_TMP" \
  tests/beta3-stubs/android/graphics/Bitmap.java \
  app/src/main/java/com/halftonestudio/{StyleKit,StyleCutGeometryPreflight,CutSafetyReviewPolicy,VinylEngine,StandardCutSafety,PhotoRevisionStore,ProjectPhotoStore,ProjectSaveGate,ProjectSourceSnapshot,ProjectArchiveReader,ProjectImageSignature,JsonSyntaxVerifier,ProjectManifest,EngineBuild,PhotoPairToken,ParallelGeometry,CancellationCheckpoint,FastGeom}.java \
  tests/V3Beta3ReliabilityTest.java
java -Xmx768m -ea -cp "$BETA3_TMP" com.halftonestudio.V3Beta3ReliabilityTest
python3 tests/check-v3-beta3-integration.py
printf 'PASS VinylStudio v3 Beta3 geometry, transaction and source snapshot gates\n'

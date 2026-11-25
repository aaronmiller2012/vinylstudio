#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
for cmd in java javac python3 bash; do command -v "$cmd" >/dev/null || { echo "Missing $cmd" >&2; exit 12; }; done
printf '\n===== RC169 RELIABILITY AND PERIMETER CUT GEOMETRY =====\n'
python3 tests/check-current-release.py
python3 tests/check-version-transition.py
python3 tests/check-rc113-rollback.py
python3 tests/check-rc169-fixes.py
T="$(mktemp -d)"; trap 'rm -rf -- "$T"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java -d "$T" tests/RC169ReliabilityGeometryTest.java tests/StudioProjectLibraryTest.java tests/StudioLibraryJobsTest.java
java -ea -Xmx850m -cp "$T" com.halftonestudio.RC169ReliabilityGeometryTest
java -ea -Xmx850m -cp "$T" com.halftonestudio.StudioProjectLibraryTest
java -ea -Xmx850m -cp "$T" com.halftonestudio.StudioLibraryJobsTest
javac -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/{StudioActivity,NineLayerCraftPatternEngine,StudioRenderController,StudioProjectBackup,StudioProjectLibrary,StudioLibraryJobs,ProjectManifest,EngineBuild,SharedExportFiles}.java
printf '\n===== INHERITED RC168 CUTTING GEOMETRY / REGRESSION =====\n'
bash tests/run-rc168-fishnet-crochet-gate.sh
printf '\nPASS RC169 reliability and source host gates\n'

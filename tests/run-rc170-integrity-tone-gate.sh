#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
for cmd in java javac python3 bash; do command -v "$cmd" >/dev/null || { echo "Missing $cmd" >&2; exit 12; }; done
printf '\n===== RC170 FISHNET TONE AND RELIABILITY EXECUTABLE TESTS =====\n'
python3 tests/check-current-release.py
python3 tests/check-version-transition.py
python3 tests/check-rc113-rollback.py
python3 tests/check-rc170-integration.py
T="$(mktemp -d)";trap 'rm -rf -- "$T"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java -d "$T" tests/RC170IntegrityToneTest.java tests/RC169ReliabilityGeometryTest.java tests/FishnetCrochetRC168Test.java tests/CamoDetailRC167Test.java tests/CamoPaletteDefaultsRC165Test.java tests/CraftPatternRC164Test.java tests/StudioProjectLibraryTest.java tests/StudioLibraryJobsTest.java tests/LibraryDocumentTruncationTest.java
java -ea -Xmx850m -cp "$T" com.halftonestudio.RC170IntegrityToneTest
java -ea -Xmx850m -cp "$T" com.halftonestudio.RC169ReliabilityGeometryTest
java -ea -Xmx850m -cp "$T" com.halftonestudio.FishnetCrochetRC168Test
java -ea -Xmx850m -cp "$T" com.halftonestudio.CamoDetailRC167Test
java -ea -Xmx850m -cp "$T" com.halftonestudio.CamoPaletteDefaultsRC165Test
java -ea -Xmx850m -cp "$T" com.halftonestudio.CraftPatternRC164Test
java -ea -Xmx850m -cp "$T" com.halftonestudio.StudioProjectLibraryTest
java -ea -Xmx850m -cp "$T" com.halftonestudio.StudioLibraryJobsTest
java -ea -Xmx850m -cp "$T" com.halftonestudio.LibraryDocumentTruncationTest
javac -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/*.java
printf '\n===== OPTIONAL DEEP INHERITED RC169 HOST SUITE =====\n'
if [[ "${FULL_TESTS:-0}" == "1" ]];then
  bash tests/run-rc169-stability-geometry-gate.sh
else
  echo 'Deep recursive historical suite available with FULL_TESTS=1; mandatory RC169-RC164 behavioral tests passed above.'
fi
printf '\nPASS RC170 executable and targeted inherited host gates\n'

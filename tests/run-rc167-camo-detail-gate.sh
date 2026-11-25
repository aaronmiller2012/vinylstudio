#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
for x in java javac python3; do command -v "$x" >/dev/null || { echo "Missing test tool: $x" >&2; exit 12; }; done
printf '\n===== RC167 PHOTO-DETAIL CAMOUFLAGE / 9 PHYSICAL CUT SHEETS =====\n'
python3 tests/check-current-release.py
python3 tests/check-version-transition.py
T="$(mktemp -d)"; trap 'rm -rf -- "$T"' EXIT
javac -J-Xmx950m -sourcepath app/src/main/java -d "$T" tests/CamoDetailRC167Test.java
java -ea -Xmx900m -cp "$T" com.halftonestudio.CamoDetailRC167Test
javac -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/{NineLayerCraftPatternEngine,PhotoStyleDefaults,StudioActivity,CamouflagePalette,VinylStyleSuite}.java
printf '\n===== INHERITED RC166 STYLE REMOVAL + COLOR / LAYER / GEOMETRY GATE =====\n'
bash tests/run-rc166-style-retirement-gate.sh
printf '\nPASS RC167 camouflage detail, physical layer geometry, cancel, color defaults, inherited picker and registration tests\n'

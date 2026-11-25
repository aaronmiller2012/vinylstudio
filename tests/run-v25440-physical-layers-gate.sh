#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
printf '\n===== RC74 PHYSICAL LAYER CONSISTENCY =====\n'
python3 tests/check-v25440-physical-layers.py
python3 tests/check-java-switch-labels.py app/src/main/java
javac -sourcepath app/src/main/java -d "$T" "$D/PhysicalLayerLayout.java" tests/PhysicalLayerLayoutRC74Test.java tests/{ParseJavaSource,CheckDuplicateJavaMethods}.java
java -ea -cp "$T" com.halftonestudio.PhysicalLayerLayoutRC74Test
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,PhysicalLayerLayout,AutoSilhouetteUnderlay}.java
java -cp "$T" CheckDuplicateJavaMethods app/src/main/java
printf '\n===== TARGETED INHERITED LAYER AND EXPORT COMPATIBILITY =====\n'
python3 tests/check-v25439-silhouette-l1.py
python3 tests/check-rc72-release-metadata.py
python3 tests/check-v25434-registration.py
python3 tests/check-v25435-colors.py
python3 tests/check-v25419-cleanup.py
T2="$(mktemp -d)"; trap 'rm -rf "$T" "$T2"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T2" \
 "$D"/{AutoSilhouetteUnderlay,VinylEngine,SolidSnipBase,RegistrationMarks,LayerCutCounts,ExportColorLabels}.java \
 tests/{AutoSilhouetteL1RC73Test,AutoSilhouetteUnderlayRC72Test,SolidSnipBaseRC71Test,RegistrationMarksTest,ExportColorLabelsRC70Test}.java
for t in AutoSilhouetteL1RC73Test AutoSilhouetteUnderlayRC72Test SolidSnipBaseRC71Test RegistrationMarksTest ExportColorLabelsRC70Test; do
 java -ea -Xmx768m -cp "$T2" "com.halftonestudio.$t"
done

printf '\nPASS RC74 AND INHERITED SOURCE GATES\n'

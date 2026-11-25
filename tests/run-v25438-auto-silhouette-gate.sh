#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
printf '\n===== RC72 RELEASE METADATA INTEGRITY =====\n'
python3 tests/check-rc72-release-metadata.py
printf '\n===== RC72 AUTOMATIC SILHOUETTE UNDERLAY =====\n'
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" \
  "$D"/{AutoSilhouetteUnderlay,VinylEngine,SolidSnipBase}.java \
  tests/AutoSilhouetteUnderlayRC72Test.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.AutoSilhouetteUnderlayRC72Test
printf '\n===== RC72 COMPILATION-SAFETY SOURCE CHECK =====\n'
python3 tests/check-java-switch-labels.py app/src/main/java
javac -sourcepath app/src/main/java -d "$T" tests/{ParseJavaSource,CheckDuplicateJavaMethods}.java
java -Xmx768m -cp "$T" ParseJavaSource "$D"/{StudioActivity,AutoSilhouetteUnderlay,VinylEngine,SolidSnipBase}.java
java -Xmx768m -cp "$T" CheckDuplicateJavaMethods app/src/main/java
python3 - <<'CHECK'
from pathlib import Path
u=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
b=Path('app/src/main/java/com/halftonestudio/AutoSilhouetteUnderlay.java').read_text()
assert 'autoSilhouetteColor' in u and '"autoSilhouetteColor"' in u
assert 'standardUnderlay=generatedBase' in u and 'specialtyUnderlay=generatedBase' in u
assert 'drawUnderlayInArtworkMm(cv,true)' in u and 'drawUnderlayInArtworkMm(cv,false)' in u
assert 'standardLayerManifest(result,base)' in u
assert 'putZip(zip,autoBaseFilename(base),svg)' in u
assert 'showAutomaticSilhouette(overview)' in u
assert 'VinylEngine.validateMarks(result)' in b
assert 'physical sheets' in u or 'physical vinyl layers' in u
print('PASS RC72 preview/export/UI/persistence routing')
CHECK
printf '\n===== RC71 HISTORICAL REGRESSION =====\n'
bash tests/run-v25437-solid-snip-gate.sh
printf '\nPASS RC72 and inherited geometry/color/registration tests\n'

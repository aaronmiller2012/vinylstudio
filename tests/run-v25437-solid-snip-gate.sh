#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
printf '\n===== RC71 PHYSICAL SNIP BASE AND LAYER STATE =====\n'
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" \
  "$D"/{VinylEngine,SolidSnipBase,LayerCutCounts}.java tests/SolidSnipBaseRC71Test.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.SolidSnipBaseRC71Test
python3 tests/check-java-switch-labels.py app/src/main/java
javac -sourcepath app/src/main/java -d "$T" tests/{ParseJavaSource,CheckDuplicateJavaMethods}.java
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,VinylEngine,SolidSnipBase}.java
java -cp "$T" CheckDuplicateJavaMethods app/src/main/java
python3 - <<'CHECK'
from pathlib import Path
u=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
v=Path('app/src/main/java/com/halftonestudio/VinylEngine.java').read_text()
assert '"silhouetteBase"' in u and '"autoDisabledForBase"' in u
assert 'offerSolidSnipBackground(overview)' in u
assert 'Path.FillType.EVEN_ODD:Path.FillType.WINDING' in u
assert 's.layers.get(i).silhouetteBase)SolidSnipBase.generate' in v
assert 'if(layer.silhouetteBase)layers.add(layer)' in v
print('PASS UI, project state, preview and physical stack integration')
CHECK
printf '\n===== RC70 INHERITED EXPORT AND COLOR GATE =====\n'
bash tests/run-v25436-export-colors-gate.sh
printf '\nPASS RC71 solid Snip and inherited tests\n'

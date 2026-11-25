#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
printf '\n===== RC70 EXPORT COLOR ACCURACY GATE =====\n'
# The host-only color identity is kept independent from Android UI classes.
javac -sourcepath app/src/main/java -encoding UTF-8 -d "$T" app/src/main/java/com/halftonestudio/ExportColorLabels.java tests/ExportColorLabelsRC70Test.java
java -ea -cp "$T" com.halftonestudio.ExportColorLabelsRC70Test
python3 - <<'PY'
from pathlib import Path
p=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'ExportColorLabels.physicalLabel(i,l.color,l.cmykChannel)' in p
assert p.count('ExportColorLabels.fileName(i,layer.color)')==2, 'manifest/export filenames diverge'
assert 'VinylEngine.cutLayer(r,i)' in p and 'VinylEngine.cutLayer(result,i)' in p
assert 'of %d enabled physical colors will be exported' in p
assert 'Every enabled color produces one SVG' not in p
print('PASS UI labels, export exclusion and manifest filename contract')
PY
bash tests/run-v25435-colors-gate.sh
printf '\nPASS RC70 source and inherited palette/registration gates\n'

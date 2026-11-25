#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$TMP" tests/OverprintCellHostTest.java
java -ea -Xmx1000m -cp "$TMP" com.halftonestudio.OverprintCellHostTest
python3 - <<'PY'
from pathlib import Path
base=Path('app/src/main/java/com/halftonestudio')
activity=(base/'StudioActivity.java').read_text()
reg=(base/'StudioThumbnailRegistry.java').read_text()
thumb=(base/'StudioStylePreview.java').read_text()
for i,name in enumerate(['Orbits','Lozenge','Diamonds','Mixed Marks'],22):
    assert f'case {i}:' in reg
    assert f'Overprint {name}' in activity
assert 'cells.overprintKind=overprintMode' in activity
assert '"overprintCellKind"' in activity
for n in range(33,37):assert f'case {n}:' in thumb
print('PASS style picker, thumbnail identities, persistence and render path')
PY

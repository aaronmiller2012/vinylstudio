#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -J-Xmx750m -sourcepath app/src/main/java:tests -d "$T" tests/V3PerceptualSwatchRepairTest.java tests/RC100PaletteOrganizationTest.java tests/SavedVinylPaletteLibraryTest.java tests/NewStylePalettePresetsTest.java
for test in V3PerceptualSwatchRepairTest RC100PaletteOrganizationTest NewStylePalettePresetsTest; do java -ea -Xmx850m -cp "$T" "com.halftonestudio.$test"; done
java -ea -cp "$T" SavedVinylPaletteLibraryTest
python3 - <<'PY'
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'if(a.length()!=8&&a.length()!=9)continue;' in s
assert 'if(newStyleActive())return newStyleColors.clone();' in s
assert s.count('savedNewStyleColors(')>=4
assert 'OpaqueOverlayPaletteMapping.saved(Arrays.copyOf(saved.baseColors,8)' in s
assert 'newStylePresetForKey(key,base)' in s
assert 'if(!newStyleActive()&&!tessellationMode&&!layeredCellMode&&!tonalStencilMode&&!cmyk' in s
print('PASS Android source wiring: nine-slot storage, legacy bounds, gallery/apply parity')
PY
python3 tests/check-v3-release-consistency.py

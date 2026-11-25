#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests:tests/android-host-stubs -d "$TMP" \
    tests/RetiredPortraitStylesTest.java tests/OverprintCellHostTest.java
java -ea -Xmx1000m -cp "$TMP" com.halftonestudio.RetiredPortraitStylesTest
java -ea -Xmx1000m -cp "$TMP" com.halftonestudio.OverprintCellHostTest
python3 - <<'PYTEST'
from pathlib import Path
import re
base=Path('app/src/main/java/com/halftonestudio')
a=(base/'StudioActivity.java').read_text()
r=(base/'StudioThumbnailRegistry.java').read_text()
p=(base/'StudioThumbnailPalette.java').read_text()
t=(base/'StudioStylePreview.java').read_text()
assert '"Painterly Cell Weave"' not in a
assert '"Ridge Lines"' not in a
assert '"multilayer.painterly_cell_weave"' not in r
assert '"art.ridges"' not in r
assert 'id>=22&&id<=25' in a and 'if(idx==26)return' in a
# Behavior now proven by V3M3StyleSelectionTest (retired Painterly -> plain photo, no cell variant).
assert 'if(savedLayered&&StyleSelection.isRetiredOverprint(savedOverprintKind)){' in a
assert re.search(r'isRetiredOverprint\(savedOverprintKind\)\)\{\s*savedLayered=false;',a)
assert 'projectSourceJson.remove("layeredCellMode")' in a
assert 'TOP_LEVEL_COUNT = 81, TESSELLATION_COUNT = 28' in r  # +5 Prismatic ribbon styles
assert 'case VinylStyleSuite.RIDGE:return ART[' not in r
assert 'case 26:return MULTILAYER[35]' not in r
assert 'VinylStyleSuite.isRetiredStyleId(storedStyleId)' in a
assert 'art(output,i>=3?i+1:i)' in t
assert 'MULTI.length!=StudioThumbnailRegistry.MULTILAYER.length' in p
assert 'VinylStyleSuite.RIDGE,VinylStyleSuite.BANKNOTE' not in a
print('PASS no picker/thumbnail/art palette regressions; historic project fallback is explicit')
PYTEST

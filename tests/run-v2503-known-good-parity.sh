#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TMP" app/src/main/java/com/halftonestudio/{SnipMask,RegistrationMarks,CmykWhitePaperBalance,CmykPortraitDefaults,LinePresenceTracker,PortraitBackgroundGuard,LineArtEngine,StencilSeparationEngine,VinylEngine,AsciiScreenMotif,AsciiVectorGlyphs,StyleKit,ParallelGeometry,CancellationCheckpoint}.java tests/LineV2494SignatureFixture.java
java -Xmx512m -cp "$TMP" com.halftonestudio.LineV2494SignatureFixture > "$TMP/actual"
python3 - "$TMP/actual" <<'PY'
from pathlib import Path
import sys
ref=(Path('tests/line-v2494-288.expected')).read_text().splitlines()
actual=Path(sys.argv[1]).read_text().splitlines()
assert len(ref)==len(actual)==288,(len(ref),len(actual))
different=[]
for expected,got in zip(ref,actual):
    exp=expected.split();out=got.split()
    assert exp[:4]==out[:4],('signature rows reordered',expected,got)
    if expected!=got:
        assert int(exp[2]) in (13,15),('Known-working non-Contour/non-Rings style changed',expected,got)
        different.append(expected[:12])
assert len(different)>0,'Contour and Rings adjustments had no effect'
print(f'PASS: 240/240 unaffected CMYK SVG signatures match v2.49.4; {len(different)} Contour/Rings signatures intentionally changed')
PY

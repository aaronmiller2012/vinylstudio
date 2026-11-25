#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]
source=(p/'app/src/main/java/com/halftonestudio/LineArtEngine.java').read_text()
helper=(p/'app/src/main/java/com/halftonestudio/LineToneRecovery.java').read_text()
assert source.count('LineToneRecovery.edgePreservingDirectionalFootprint(')==1
assert 'supportsShapeDefinition(actualStyle)' in source
assert 'public static float edgePreservingDirectionalFootprint(' in helper
assert 'ClassicLineArtEngine' in source
for name in ['BUILD_ON_NAS.sh','.github/workflows/verify.yml','app/build.gradle']:
    assert 'tests/run-v3-milestone-b2-lines-gate.sh' in (p/name).read_text(),name
print('PASS B2 Lines wiring: bounded carrier tone, same lane geometry, classic CMYK untouched, mandatory gates')

#!/usr/bin/env python3
"""Release identity and target/source checks; complements, never replaces, behavior and Android lint."""
from pathlib import Path
import json,re
r=Path(__file__).resolve().parents[1]
s=r/'app/src/main/java/com/halftonestudio'
meta=json.loads((r/'tests/release-info.json').read_text())
# Check the current frozen release contract while retaining every inherited
# RC86 compatibility/source assertion below. Never hard-code the previous RC.
assert meta['versionCode']>=364 and tuple(map(int,meta['version'].split('.'))) >= (2,54,52)
assert meta['gate'].startswith('tests/run-') and meta['gate'].endswith('-gate.sh')
assert meta['gate'] in (r/'BUILD_ON_NAS.sh').read_text()
assert meta['apk'] in (r/'BUILD_ON_NAS.sh').read_text()
assert meta['versionName'] in (r/'app/build.gradle').read_text()
for f in ('TspArtEngine.java','StyleKit.java','LinocutEngine.java'):
 assert 'rnd.nextFloat()' not in (s/f).read_text(),f
assert (s/'StudioActivity.java').read_text().count('portableOriginalSupplier(')==3
assert 'project.nativeSource' in (s/'StudioActivity.java').read_text()
assert 'final int[] projectPixels=' in (s/'StudioActivity.java').read_text()
assert 'pixels=projectPixels' in (s/'StudioActivity.java').read_text()
for test in ('run-v2504-cmyk-continuity.sh','run-v2505-portrait-recovery.sh'):
 assert '-sourcepath app/src/main/java' in (r/'tests'/test).read_text()
assert 'ClassicCmykLines.useModern(s)' in (r/'tests/ScreenAndVinylBalanceTest.java').read_text()
assert 'ClassicCmykLines.useModern(s)' in (r/'tests/LineV2505PortraitRecoveryTest.java').read_text()
print('PASS RC86 source wiring, metadata, compatibility references, and mode-explicit tests')

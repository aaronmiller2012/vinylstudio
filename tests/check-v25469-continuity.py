#!/usr/bin/env python3
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
meta=json.loads((root/'tests/release-info.json').read_text())
assert tuple(map(int,meta['version'].split('.')))>=(2,54,69) and meta['versionCode']>=381
assert meta['gate'].startswith('tests/run-v254')
assert meta['apk'].startswith('VinylHalftoneStudio-v'+meta['version'])
assert meta['versionName'] in (root/'app/build.gradle').read_text()
assert meta['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
assert ('bash '+meta['gate']) in (root/'BUILD_ON_NAS.sh').read_text()
java=root/'app/src/main/java/com/halftonestudio'
lines=(java/'LineArtEngine.java').read_text()
engine=(java/'VinylEngine.java').read_text()
assert 'sourceCoverage*=Math.min(1f,sheetScale)' not in lines
assert 'r.opaqueLineHasAlphaCutout' in lines
assert 'result.opaqueLineHasAlphaCutout=true' in engine
assert 'LinePresenceTracker.extract' in lines
assert 'ClassicLineArtEngine.generate' in lines
assert 'previousAllowedX' in lines and 's.snips.safeSegment' in lines
assert 'result.opaqueLineScales=null;result.opaqueLineDetails=null' in engine
assert not list(java.glob('Typography*.java'))
print('PASS RC103 release/installer metadata, alpha mask and source evidence, physical safety wiring')

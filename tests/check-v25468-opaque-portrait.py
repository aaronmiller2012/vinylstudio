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
engine=(java/'VinylEngine.java').read_text()
lines=(java/'LineArtEngine.java').read_text()
preset=(java/'SmartHalftonePresets.java').read_text()
activity=(java/'StudioActivity.java').read_text()
assert 'OpaqueCmykLineBudget.prepare' in engine and 'OpaqueCmykLineBudget.localDetail' in engine
assert 'result.opaqueLineScales=null;result.opaqueLineDetails=null' in engine
assert 'float minHalf=s.minimumMm*.5f+.012f' in lines
assert 'laneNumber*.61803398875' in lines
assert 'sourceCoverage*=Math.min(1f,sheetScale)' not in lines
assert 'settings.opaqueLineBalance=' in preset
assert 'out.put("opaqueLineBalance",s.opaqueLineBalance)' in activity
assert 's.opaqueLineBalance=json.optBoolean("opaqueLineBalance",false)' in activity
assert 'ClassicLineArtEngine.generate' in lines
assert 'previousAllowedX' in lines and 's.snips.safeSegment' in lines
assert 'ClassicCmykLines.apply(settings)' in activity
assert not list(java.glob('Typography*.java'))
print('PASS RC102 metadata, original photo and Snip preservation, new-style opt-in, color and cut safety wiring')

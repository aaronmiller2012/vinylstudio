#!/usr/bin/env python3
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
meta=json.loads((root/'tests/release-info.json').read_text())
assert tuple(map(int,meta['version'].split('.')))>=(2,54,67) and meta['versionCode']>=379
assert meta['gate'].startswith('tests/run-v254')
assert meta['apk'].startswith('VinylHalftoneStudio-v'+meta['version'])
assert meta['versionName'] in (root/'app/build.gradle').read_text()
assert meta['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
assert ('bash '+meta['gate']) in (root/'BUILD_ON_NAS.sh').read_text()
java=root/'app/src/main/java/com/halftonestudio'
lines=(java/'LineArtEngine.java').read_text()
preset=(java/'SmartHalftonePresets.java').read_text()
policy=(java/'LineToneRecovery.java').read_text()
diagnostics=(java/'LineCutDiagnostics.java').read_text()
assert 'new LinePresenceTracker.Sample(x,y,half,sourceCoverage,sourceCoverage>.008f,subject)' in lines
assert 'previousAllowedX' in lines and 's.snips.safeSegment' in lines
assert 'LineToneRecovery.directionalFootprint' in lines
assert 'LineToneRecovery.widthResponse' in lines
assert 'ClassicLineArtEngine.generate' in lines
assert 'ClassicCmykLines.useModern(settings)' in preset
assert 'ClassicCmykLines.apply(settings)' in (java/'StudioActivity.java').read_text()
assert 'class LineToneRecovery' in policy
assert 'Arrays.sort(sorted' in diagnostics
assert not list(java.glob('Typography*.java'))
print('PASS RC101 source, default modern lines, masked continuity, static legacy option, package metadata')

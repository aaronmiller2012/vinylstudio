#!/usr/bin/env python3
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=459 and meta['apk'].startswith('VinylHalftoneStudio-v'+meta['versionName']+'-') and meta['versionName'].startswith('3.') and meta['apk'].endswith('.apk')
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
start=s.index('private ArrayList<PickerChoice> collectStylePickerChoices(')
stop=s.index('private void showTessellationLayoutPicker()',start)
assert 'if(!TessellationMotifEngine.selectablePattern(i))continue;' in s[start:stop]
assert 'newStyleVariant=NewStyleVariantPolicy.restore(newStyleId,savedVariant)' in s
assert 'Retired tessellation changed to Trihexagonal' in s
engine=(r/'app/src/main/java/com/halftonestudio/TessellationMotifEngine.java').read_text()
for symbol in ('INTERLOCKING_DUMBBELL','DIAMOND_STAR','CIRCLE_CHAIN_LATTICE','DIAMOND_ARC_ROSETTE','OCTAGON_DIAMOND_LINKS'):
 assert 'pattern=='+symbol in engine
assert 'PATTERN_SLOT_COUNT=OCTAGON_DIAMOND_LINKS+1;' in engine
assert ('versionCode '+str(meta['versionCode'])) in (r/'app/build.gradle').read_text()
print('PASS Beta8 UI picker exclusion and compatible project archive restore')

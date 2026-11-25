#!/usr/bin/env python3
from release_version import at_least
from pathlib import Path
import json,re
p=Path(__file__).resolve().parents[1]
s=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
i=json.loads((p/'tests/release-info.json').read_text())
assert i['versionCode']>=359 and at_least(i['versionName'], '2.54.0')
assert 'private void paletteStrip(' in s
assert 'paletteStrip(tile,colors,active);' in s
assert 'paletteStrip(item,displayColors' in s
assert 'chip.setAlpha(.30f)' in s
assert 'newStylePresetForKey(key,base)' in s
assert 'VinylPaletteMapping.forStyle(' in s
assert 'VinylPaletteMapping.processEight(' in s
assert 'selectedPaletteKey=key;' in s
assert 'savedNewStyleColors' in s and 'NewStyleLayerPolicy.isStructuralLayer(newStyleId,0)' in s
assert 'selected?dp(128):dp(81)' not in s
assert 'selected?ACCENT' in s
print('PASS RC81 unified inline, modal and saved strips; active/inactive layer visibility; stable selected state')

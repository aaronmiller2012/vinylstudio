#!/usr/bin/env python3
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
o=(r/'app/src/main/java/com/halftonestudio/OffsetCellPortraitEngine.java').read_text()
p=(r/'app/src/main/java/com/halftonestudio/ProjectSettingsSchema.java').read_text()
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=406 and meta['gate'].startswith('tests/run-')
# M5 / ADR-0006: the same single action, labelled per family ("Swap colors" for classic CMYK,
# "Reverse tones" otherwise); it must still be wired to reverseActiveVinylPalette.
assert 'TextView reverse=action(paletteActions,getString(cmykSwap?R.string.m5_reverse_label_cmyk:R.string.m5_reverse_label_tones),this::reverseActiveVinylPalette);' in s
assert 'LinearLayout paletteActions=horizontal(host);' in s
assert 'togglePaletteOrientation(' not in s
assert 'TextView reverse=action(tile,' not in s
assert 'TextView reverse=action(buttons,' not in s
assert 'if(!offsetCellMode){' in s
assert ('layerExtraMotif=!offsetCellMode&&layeredExtraMotif' in s) or ('layerExtraMotif=(capMode||scaleMode)&&layeredExtraMotif' in s)
assert ('out.put("layeredExtraMotif",!offsetCellMode&&layeredExtraMotif)' in s) or ('out.put("layeredExtraMotif",(bottleCapMode||fishScaleMode)&&layeredExtraMotif)' in s)
assert 'o.solidBase=true;' in o and 'o.detailMotifOverlay=false;' in o
assert ('CURRENT=28;' in p) or ('CURRENT=29;' in p)
print('PASS RC125 reverse location, per-card removal, fixed Offset geometry, schema 27')

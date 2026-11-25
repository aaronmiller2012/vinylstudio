#!/usr/bin/env python3
"""RC66 routing and serialized-style invariant checks."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]; d=r/'app/src/main/java/com/halftonestudio'
u=(d/'StudioActivity.java').read_text();e=(d/'VinylEngine.java').read_text()
info=json.loads((r/'tests/release-info.json').read_text())
assert info['versionCode']>=344 and info['gate'] in ('tests/run-v25432-multilayer-words-gate.sh','tests/run-v25433-word-stencil-gate.sh','tests/run-v25434-registration-gate.sh','tests/run-v25435-colors-gate.sh','tests/run-v25436-export-colors-gate.sh','tests/run-v25437-solid-snip-gate.sh','tests/run-v25438-auto-silhouette-gate.sh','tests/run-v25439-silhouette-l1-gate.sh','tests/run-v25440-physical-layers-gate.sh','tests/run-v25441-visible-controls-gate.sh','tests/run-v25442-palette-tap-gate.sh','tests/run-v25443-cut-sheet-gate.sh','tests/run-v25444-reliability-gate.sh','tests/run-v25445-glass-portrait-gate.sh','tests/run-v25446-glass-cutfix-gate.sh','tests/run-v25447-palette-harmony-gate.sh')
assert info['versionName'] in (r/'app/build.gradle').read_text()
assert info['gate'] in (r/'BUILD_ON_NAS.sh').read_text()
multi=u[u.index('private void showMultilayerPickerDialog()'):u.index('private void stylePickerBack(')]
shape=u[u.index('private void showShapePickerDialog()'):u.index('private void selectSpaceCurve(')]
if info['versionCode']>=346:
    assert 'activateTextPortrait(1)' not in multi and 'activateTextPortrait(2)' not in multi
else:
    assert ('"Word Portrait"' in multi or '"Word Mosaic"' in multi) and 'activateTextPortrait(1)' in multi
assert 'VinylEngine.SHAPE_STYLES' in shape
assert 'DASH,ASCII};' in e and 'ASCII=27,WORD_SCREEN=28' in e
assert 'WordScreenMotif.generate(result,pixels,w,h,i)' in e, 'old shape 28 saved files must still open'
assert 'case VinylEngine.WORD_SCREEN:' in u, 'old word-shapes saved project missing'
assert 'newStyleId==VinylStyleSuite.TEXT||' in u and 'AutoSilhouetteUnderlay.specialtyEligible(newStyleId,newStyleVariant,false))?0:settings.snipFillMode' in u, 'text mode must retain its Snip-mask fill override'
assert 'newStyleId!=VinylStyleSuite.TEXT' in u, 'multilayer word selected should not have ASCII variant toggle'
assert 'WordVectorGlyphs.contours' in (d/'WordPortraitEngine.java').read_text()
assert 'o.silhouetteUnderlay=true' in (d/'VinylStyleSuite.java').read_text()
assert 'android.useAndroidX=true' in (r/'gradle.properties').read_text()
assert not list(d.glob('*DotToDot*'))
print('PASS RC66 Word Portrait moved back to Multilayer, ASCII Shapes unchanged, serialized IDs preserved')

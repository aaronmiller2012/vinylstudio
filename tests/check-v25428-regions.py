#!/usr/bin/env python3
"""Release wiring and source-preservation contract for RC62."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
d=r/'app/src/main/java/com/halftonestudio'
u=(d/'StudioActivity.java').read_text()
e=(d/'TextHalftoneEngine.java').read_text()
suite=(d/'VinylStyleSuite.java').read_text()
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=340
assert meta['versionName'] in (r/'app/build.gradle').read_text()
assert meta['gate'] in (r/'BUILD_ON_NAS.sh').read_text()
assert '"Multilayer ASCII Portrait"' not in u and '"Multilayer Word Portrait"' not in u
assert 'showShapePickerDialog()' in u and 'VinylEngine.WORD_SCREEN' in u
assert 'activateTextPortrait(int variant)' in u and 'activateNewStyle(VinylStyleSuite.TEXT)' in u
assert 'newStyleVariant=variant' in u and 'activateNewStyle(VinylStyleSuite.TEXT)' in u
assert 'newStyleId==VinylStyleSuite.TEXT?0:settings.snipFillMode' in u
assert 'o.silhouetteUnderlay=true' in suite
assert 'TextPortraitRegions.analyze(src,cell)' in e
assert 'AsciiPortraitEngine.render(' in e and 'WordPortraitEngine.render(' in e
assert 'VinylStyleSuite.subjectLoops(src)' in e
assert 'snipped-background-1' in e
for name in ['TextPortraitRegions','AsciiPortraitEngine','WordPortraitEngine']:
    assert (d/(name+'.java')).exists()
assert 'android.useAndroidX=true' in (r/'gradle.properties').read_text()
assert not list(d.glob('*DotToDot*'))
print('PASS RC62 version, Shapes routing, distinct engines, stable IDs, filled Snip, AndroidX')

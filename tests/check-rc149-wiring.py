#!/usr/bin/env python3
"""RC149 integration: new stable ID, precise physical layers, UI and saved projects."""
from pathlib import Path
import json,csv
r=Path(__file__).resolve().parents[1]
j=r/'app/src/main/java/com/halftonestudio'
def read(n):return (j/n).read_text()
engine=read('PrismaticDiagonalPortraitEngine.java')
suite=read('VinylStyleSuite.java');activity=read('StudioActivity.java')
meta=json.loads((r/'tests/release-info.json').read_text())
assert 'PRISMATIC_DIAGONAL=26' in suite
assert 'c.style==PRISMATIC_DIAGONAL' in suite and 'PrismaticDiagonalPortraitEngine.generate(' in suite
assert 'case VinylStyleSuite.PRISMATIC_DIAGONAL:' in read('NewStyleVariantPolicy.java')
assert 'style == VinylStyleSuite.PRISMATIC_DIAGONAL' in read('NewStyleColorSlots.java')
assert '"multilayer.prismatic_diagonal"' in read('StudioThumbnailRegistry.java')
assert 'case 12: // prismatic diagonal:' in read('StudioStylePreview.java')
assert 'addPickerChoice(out,multi,"Prismatic Diagonal Portrait"' in activity
assert 'slider(host,"Spacing · mm"' in activity
for k in ('prismaticSpacingMm','prismaticIrregularity','prismaticRoundness','prismaticFeature'):
    assert 'out.put("'+k+'"' in activity and 'json.opt' in activity and k in activity
    assert k in (suite+activity)
for k in ('StyleKit.Source','TopographicLayersEngine.contours','StyleKit.checkCancelled()',
          'effectiveGap','open(mask,',
          'new StyleKit.Layer(name,color)','"prismatic-diagonal-"'):
    assert k in engine,k
if meta['versionCode']>=521:
    assert 'f.photo.lum[f.photo.pixelIndex(px,py)]' in engine and 'sampleWithinCell(f,px,py,' in engine
    assert 'roundedEnd(' in engine
else: assert 'lumAt' in engine
if meta['versionCode']>=519: assert 'new BandField' in engine and 'carve(' not in engine
keys=list(csv.DictReader((r/'tests/RC133-THUMBNAILS.csv').open()))
assert len({row['thumbnail_key'] for row in keys})==len(keys) and any(row['thumbnail_key']=='multilayer.prismatic_diagonal' for row in keys)
assert meta['versionCode']>=426 and (meta['versionCode']>=519 or meta['gate'] in ('tests/run-rc149-prismatic-gate.sh','tests/run-rc150-prismatic-tone-gate.sh','tests/run-rc151-thumbnails-gate.sh'))
assert f"versionCode {meta['versionCode']}" in (r/'app/build.gradle').read_text()
assert meta['gate'] in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS RC149 style registration, user controls, settings, thumbnail, output and release metadata')

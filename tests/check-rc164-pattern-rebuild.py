#!/usr/bin/env python3
"""RC164 wiring and stable IDs; executable geometry tests run in CraftPatternRC164Test."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1];src=r/'app/src/main/java/com/halftonestudio'
m=json.loads((r/'tests/release-info.json').read_text())
assert m['versionCode']==441 and m['version']=='2.55.29'
assert m['gate']=='tests/run-rc164-pattern-rebuild-gate.sh'
assert m['apk']=='VinylHalftoneStudio-v2.55.29-PatternRebuild-RC164.apk'
suite=(src/'VinylStyleSuite.java').read_text()
for key in ('CAMOUFLAGE=35','LEAF_VEIN=36','FISHNET=37','LACE=38','CROCHET=39',
            'CAMO_DESERT=40','CAMO_URBAN=41','CAMO_DIGITAL=42'):
    assert key in suite,key
ui=(src/'StudioActivity.java').read_text()
for key in ('VinylStyleSuite.CAMO_DESERT','VinylStyleSuite.CAMO_URBAN',
            'VinylStyleSuite.CAMO_DIGITAL','CamouflagePalette.defaults(id,'):
    assert key in ui,key
pattern=(src/'NineLayerCraftPatternEngine.java').read_text()
for key in ('leafScaffold(surface','curved(surface','surface.fillValid(0)',
            'assignQuantiles(cells,colors,o.layers)','CAMO_DIGITAL','surface.polygon('):
    assert key in pattern,key
assert 'TOP_LEVEL_COUNT = 70' in (src/'StudioThumbnailRegistry.java').read_text() or 'TOP_LEVEL_COUNT = 68' in (src/'StudioThumbnailRegistry.java').read_text() or 'TOP_LEVEL_COUNT = 71' in (src/'StudioThumbnailRegistry.java').read_text()
assert 'case 27: // U.S.-inspired digital' in (src/'StudioStylePreview.java').read_text()
assert 'CAMO_DIGITAL' in (src/'NewStyleVariantPolicy.java').read_text()
assert 'CAMO_DIGITAL' in (src/'PhotoStyleDefaults.java').read_text() or '76,74,78}' in (src/'PhotoStyleDefaults.java').read_text()
assert 'versionCode 441' in (r/'app/build.gradle').read_text()
assert m['gate'] in (r/'BUILD_ON_NAS.sh').read_text()
assert m['gate'] in (r/'.github/workflows/verify.yml').read_text()
print('PASS RC164 IDs, picker, material defaults, thumbnails, 9-slot foundation and release wiring')

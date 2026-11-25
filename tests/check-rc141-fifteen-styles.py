#!/usr/bin/env python3
"""Static release contract for the RC141 fifteen-style expansion."""
from pathlib import Path
import csv
import json

root=Path(__file__).resolve().parents[1]
engine=(root/'app/src/main/java/com/halftonestudio/ExpandedPortraitStylesEngine.java').read_text()
suite=(root/'app/src/main/java/com/halftonestudio/VinylStyleSuite.java').read_text()
activity=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
registry=(root/'app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java').read_text()
slots=(root/'app/src/main/java/com/halftonestudio/NewStyleColorSlots.java').read_text()
variants=(root/'app/src/main/java/com/halftonestudio/NewStyleVariantPolicy.java').read_text()
draft=(root/'app/src/main/java/com/halftonestudio/RenderDraftPolicy.java').read_text()
build=(root/'app/build.gradle').read_text()
meta=json.loads((root/'tests/release-info.json').read_text())

names=[
    'Organic Marquetry Portrait','Fingerprint Portrait','Connected Papercut Lace',
    'Reduction Woodcut','Woven Ribbon Portrait','Scratchboard / Mezzotint',
    'Delaunay Wireframe Portrait','Continuous Scribble Portrait','Radial String-Art Portrait',
    'Kintsugi Crackle Mosaic','Flowing Woodgrain Portrait','Circuit Trace Portrait',
    'Celtic Braid Portrait','Torn-Paper Collage','Architectural Contour Cut'
]
constants=[
    'MARQUETRY=11','FINGERPRINT=12','CONNECTED_LACE=13','REDUCTION_WOODCUT=14',
    'WOVEN_RIBBON=15','SCRATCHBOARD=16','DELAUNAY_WIREFRAME=17',
    'CONTINUOUS_SCRIBBLE=18','STRING_ART=19','KINTSUGI=20','WOODGRAIN=21',
    'CIRCUIT_TRACE=22','CELTIC_BRAID=23','TORN_COLLAGE=24','ARCHITECTURAL=25'
]
for name in names:
    assert f'"{name}"' in engine and f'"{name}"' in suite, name
for constant in constants:
    assert constant in suite, constant
assert 'c.style>=MARQUETRY&&c.style<=ARCHITECTURAL' in suite
assert 'ExpandedPortraitStylesEngine.generate(pixels,width,height,o)' in suite

# One bounded implementation shares image sampling, palette caching, mask union,
# cleanup, cancellation, and physical-output validation across all algorithms.
for contract in ('Math.min(300,Math.max(112,112+detail*2))','cache=new int[32768]',
                 'StyleKit.checkCancelled()','result.layers.size()>8','layer.loops.size()>5000',
                 'StyleKit.selfIntersections(loop,1)','private static void addMaskLayer'):
    assert contract in engine, contract
for method in ('marquetry(','fingerprint(','papercutLace(','reductionWoodcut(','woven(',
               'scratchboard(','wireframe(','scribble(','stringArt(','kintsugi(',
               'woodgrain(','circuit(','celtic(','architectural('):
    assert method in engine, method
assert 'style==DELAUNAY_WIREFRAME||style==CONTINUOUS_SCRIBBLE||style==STRING_ART?2:1' in engine
assert 'styleId!=VinylStyleSuite.STRING_ART' in draft

# UI integration is data-driven and every style owns an exact preview key.
assert 'for(int local=0;local<ExpandedPortraitStylesEngine.NAMES.length;local++)' in activity
assert 'ExpandedPortraitStylesEngine.CATEGORIES[local]' in activity
assert 'ExpandedPortraitStylesEngine.THUMBNAIL_KEYS[local]' in activity
assert 'ExpandedPortraitStylesEngine.DESCRIPTIONS[newStyleId-VinylStyleSuite.MARQUETRY]' in activity
assert 'minimumSelectable(newStyleId)' in activity and 'maximumSelectable(newStyleId)' in activity
assert 'TOP_LEVEL_COUNT = 62, TESSELLATION_COUNT = 22' in registry
for key in ('art.marquetry','art.fingerprint','art.papercut_lace','art.reduction_woodcut',
            'art.woven_ribbon','art.scratchboard','art.delaunay_wireframe','art.scribble',
            'art.string_art','art.kintsugi','art.woodgrain','art.circuit_trace',
            'art.celtic_braid','art.torn_collage','art.architectural'):
    assert f'"{key}"' in registry, key
assert 'VinylStyleSuite.ARCHITECTURAL' in slots and 'VinylStyleSuite.ARCHITECTURAL' in variants
rows=list(csv.DictReader((root/'tests/RC133-THUMBNAILS.csv').open()))
assert len(rows)==84 and len({row['thumbnail_key'] for row in rows})==84

assert meta=={
    'version':'2.55.6','versionCode':418,
    'versionName':'2.55.6-fifteen-styles-rc141',
    'apk':'VinylHalftoneStudio-v2.55.6-FifteenStyles-RC141.apk',
    'gate':'tests/run-rc141-fifteen-styles-gate.sh'
}
assert 'versionCode 418' in build and "versionName '2.55.6-fifteen-styles-rc141'" in build
print('PASS RC141 fifteen stable styles, bounded shared engine, UI contracts, and release identity')

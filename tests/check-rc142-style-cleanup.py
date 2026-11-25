#!/usr/bin/env python3
"""Static release contract for the reviewed RC142 style cleanup."""
from pathlib import Path
import csv
import json

root=Path(__file__).resolve().parents[1]
engine=(root/'app/src/main/java/com/halftonestudio/ExpandedPortraitStylesEngine.java').read_text()
suite=(root/'app/src/main/java/com/halftonestudio/VinylStyleSuite.java').read_text()
activity=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
registry=(root/'app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java').read_text()
preview=(root/'app/src/main/java/com/halftonestudio/StudioStylePreview.java').read_text()
slots=(root/'app/src/main/java/com/halftonestudio/NewStyleColorSlots.java').read_text()
variants=(root/'app/src/main/java/com/halftonestudio/NewStyleVariantPolicy.java').read_text()
defaults=(root/'app/src/main/java/com/halftonestudio/PhotoStyleDefaults.java').read_text()
build=(root/'app/build.gradle').read_text()
meta=json.loads((root/'tests/release-info.json').read_text())

approved={
    11:('Organic Marquetry Portrait','art.marquetry'),
    12:('Fingerprint Portrait','art.fingerprint'),
    14:('Reduction Woodcut','art.reduction_woodcut'),
    15:('Woven Ribbon Portrait','art.woven_ribbon'),
    21:('Flowing Woodgrain Portrait','art.woodgrain'),
    24:('Torn-Paper Collage','art.torn_collage'),
}
retired={
    13:('Connected Papercut Lace','art.papercut_lace','papercutLace'),
    16:('Scratchboard / Mezzotint','art.scratchboard','scratchboard'),
    17:('Delaunay Wireframe Portrait','art.delaunay_wireframe','wireframe'),
    18:('Continuous Scribble Portrait','art.scribble','scribble'),
    19:('Radial String-Art Portrait','art.string_art','stringArt'),
    20:('Kintsugi Crackle Mosaic','art.kintsugi','kintsugi'),
    22:('Circuit Trace Portrait','art.circuit_trace','circuit'),
    23:('Celtic Braid Portrait','art.celtic_braid','celtic'),
    25:('Architectural Contour Cut','art.architectural','architectural'),
}

# Stable IDs survive as tombstones; no approved style is renumbered.
for token in ('MARQUETRY=11','FINGERPRINT=12','CONNECTED_LACE=13','REDUCTION_WOODCUT=14',
              'WOVEN_RIBBON=15','SCRATCHBOARD=16','DELAUNAY_WIREFRAME=17',
              'CONTINUOUS_SCRIBBLE=18','STRING_ART=19','KINTSUGI=20','WOODGRAIN=21',
              'CIRCUIT_TRACE=22','CELTIC_BRAID=23','TORN_COLLAGE=24','ARCHITECTURAL=25'):
    assert token in suite,token
compact=''.join(engine.split())
assert 'privatestaticfinalboolean[]ACTIVE={true,true,false,true,true,false,false,false,false,false,true,false,false,true,false};' in compact
assert 'isRetiredStyleId(c.style)' in suite
assert '!isRetiredStyleId(savedId)' in suite

# Only the six reviewed additions remain discoverable and renderable.
for style_id,(name,key) in approved.items():
    assert f'"{name}"' in engine,name
    assert f'"{key}"' in registry,key
    assert f'case VinylStyleSuite.' in registry
for style_id,(name,key,method) in retired.items():
    assert name not in engine,name
    assert name not in suite,name
    assert f'"{key}"' not in registry,key
    assert f'private static void {method}(' not in engine,method
    assert f'private static float[] {method}(' not in engine,method
assert 'if(!ExpandedPortraitStylesEngine.isActive(local))continue;' in activity
assert 'Retired or unknown expanded style' in engine
assert 'Retired or unknown expanded style' in engine
assert 'photo and Snip retained' in activity
assert 'style == VinylStyleSuite.CONNECTED_LACE' not in slots
for constant in ('SCRATCHBOARD','DELAUNAY_WIREFRAME','CONTINUOUS_SCRIBBLE','STRING_ART',
                 'KINTSUGI','CIRCUIT_TRACE','CELTIC_BRAID','ARCHITECTURAL'):
    assert f'case VinylStyleSuite.{constant}' not in variants,constant

# Picker registry and preview index shrink by exactly the nine rejected cards.
rows=list(csv.DictReader((root/'tests/RC133-THUMBNAILS.csv').open()))
assert len(rows)==76 and len({row['thumbnail_key'] for row in rows})==76
assert 'TOP_LEVEL_COUNT = 54, TESSELLATION_COUNT = 22' in registry
assert 'case 10: // torn-paper collage' in preview
for _,key,_ in retired.values():
    assert key not in {row['thumbnail_key'] for row in rows},key

# Defaults retain the 0–25 stable index layout, with retired slots disabled.
for index in (13,16,17,18,19,20,22,23,25):
    assert index < 26
assert 'final int[] base=' in defaults and 'final int[] boosts=' in defaults

assert meta['versionCode']>=420 and meta['version'].startswith('2.55.')
assert meta['versionName'] in build and ('versionCode '+str(meta['versionCode'])) in build
assert (root/meta['gate']).is_file() and meta['gate'] in (root/'BUILD_ON_NAS.sh').read_text()
print('PASS RC142: nine reviewed styles retired, six approved additions retained, IDs stable')

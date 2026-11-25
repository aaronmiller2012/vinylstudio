#!/usr/bin/env python3
"""Append-only RC152 style IDs, UI, export and thumbnail contract."""
from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/halftonestudio'
def read(name):return (base/name).read_text()
suite=read('VinylStyleSuite.java');ui=read('StudioActivity.java')
registry=read('StudioThumbnailRegistry.java')
preview=read('StudioStylePreview.java')
engine=read('PatternPortraitTrioEngine.java')
slots=read('NewStyleColorSlots.java')
policy=read('NewStyleVariantPolicy.java')
photo=read('PhotoStyleDefaults.java')
colors=read('StudioThumbnailPalette.java')
meta=json.loads((root/'tests/release-info.json').read_text())
expected={'version':'2.55.17','versionCode':429,'versionName':'2.55.17-three-styles-rc152',
'apk':'VinylHalftoneStudio-v2.55.17-ThreeStyles-RC152.apk',
'gate':'tests/run-rc152-three-styles-gate.sh'}
assert meta==expected,meta
assert 'PRISMATIC_DIAGONAL=26' in suite
assert 'CONFETTI=27, CHAINMAIL=28, SPIDER_WEB=29' in suite
for kind in ('CONFETTI','CHAINMAIL','SPIDER_WEB'):
 assert f'VinylStyleSuite.{kind}' in ui,kind
 assert f'case VinylStyleSuite.{kind}:' in policy,kind
 assert f'case VinylStyleSuite.{kind}:return ' in registry,kind
 assert f'style=={kind}' in suite,kind
assert len(re.findall(r'"(?:Confetti Portrait|Chainmail Portrait|Spider Web Portrait)"',suite))==3
assert 'style == VinylStyleSuite.CONFETTI' in slots
assert 'style == VinylStyleSuite.CHAINMAIL' in slots
assert 'style == VinylStyleSuite.SPIDER_WEB' not in slots
assert 'new StyleKit.Layer(prefix+"-"+(slot+1)' in engine
assert 'TopographicLayersEngine.contours' in engine
assert 'StyleKit.checkCancelled()' in engine
assert 'o.allowed' in suite and 'valid' in engine
assert 'private int confetti(' in engine or 'private static int confetti(' in engine
assert 'private static int chainmail(' in engine
assert 'private static int spiderWeb(' in engine
assert 'if(type==15)' in preview and 'if(type==16)' in preview and 'if(type==8)' in preview
assert 'SHAPES[15]' in registry and 'SHAPES[16]' in registry and 'LINES[8]' in registry
assert 'TOP_LEVEL_COUNT = 57, TESSELLATION_COUNT = 22' in registry
assert 'trioSpacingMemory' in ui and 'trioOrganicMemory' in ui
assert 'entry.put("trioSpacingMm"' in ui and 'item.optDouble("trioSpacingMm"' in ui
assert 'entry.put("trioOrganic"' in ui and 'item.optInt("trioOrganic"' in ui
assert '"Spacing · mm"' in ui and '"Weave variation"' in ui
assert 'Cyan' not in colors or 'SHAPE_ORDERS' in colors
for token in ('75,67,72','17,13,16'):
 assert token in photo,('PhotoStyleDefaults missing new IDs',token)
for script in ('PHONE_BUILD_AND_RECEIVE.sh','PHONE_RECEIVE_ONLY.sh','NAS_STAGE_BUILD.sh','VERIFY_PACKAGE.sh'):
 text=(root/'transfer'/script).read_text()
 assert 'RC151' not in text,script
 assert 'RC152' in text,script
print('PASS RC152 style routing, 3 new IDs, masks, palette slots, persistence and packaging contracts')

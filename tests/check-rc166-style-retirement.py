#!/usr/bin/env python3
"""RC166 UI integration and release wiring; see JVM gate for actual behavior."""
import json,re
from pathlib import Path
p=Path(__file__).resolve().parents[1]
s=p/'app/src/main/java/com/halftonestudio'
info=json.loads((p/'tests/release-info.json').read_text())
assert info['versionCode']>=443 and info['version']>='2.55.31', 'Retirement metadata regression'
ui=(s/'StudioActivity.java').read_text()
start=ui.index('private ArrayList<PickerChoice> collectStylePickerChoices(')
stop=ui.index('private void showTessellationLayoutPicker()',start)
picker=ui[start:stop]
assert 'VinylStyleSuite.LEAF_VEIN' not in picker and 'VinylStyleSuite.LACE' not in picker
assert 'TessellationMotifEngine.PATTERN_SLOT_COUNT' in picker
assert 'selectTessellationLayout(pattern)' in picker
assert 'if(!TessellationMotifEngine.selectablePattern(pattern))return;' in ui
assert 'newStyleVariant=NewStyleVariantPolicy.restore(newStyleId,savedVariant);' in ui
assert 'Retired tessellation changed to Trihexagonal' in ui
reg=(s/'StudioThumbnailRegistry.java').read_text()
# RC166's original 71 top-level thumbnails predate the later V3 additions.
# Retain an exact count for V3 and verify actual declarations, not just a number.
expected=76 if info['versionName'].startswith('3.') else 71
assert f'TOP_LEVEL_COUNT = {expected}, TESSELLATION_COUNT = 28;' in reg
keys=[]
for family in ('MULTILAYER','SHAPES','LINES','SINGLE','ART'):
    match=re.search(r'String\[\] '+family+r'\s*=\s*\{(.*?)\};',reg,re.S)
    assert match, 'Missing thumbnail family '+family
    keys.extend(re.findall(r'"([^"]+)"',match.group(1)))
assert len(keys)==expected and len(set(keys))==expected, 'Missing or duplicate thumbnail keys'
assert 'multilayer.leaf_vein' not in reg and 'multilayer.lace' not in reg
assert info['gate'] in (p/'BUILD_ON_NAS.sh').read_text()
assert info['gate'] in (p/'.github/workflows/verify.yml').read_text()
assert re.search(r'\bversionCode\s+'+str(info['versionCode'])+r'\b',(p/'app/build.gradle').read_text())
print('PASS RC166 picker retirement, safe variant restore, current thumbnail registry, version and NAS/CI wiring')

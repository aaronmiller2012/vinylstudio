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
# Registry size is derived from its declarations, while the stable 561 inventory
# is pinned independently. New styles may append; existing identities cannot vanish.
count=re.search(r'TOP_LEVEL_COUNT\s*=\s*(\d+),\s*TESSELLATION_COUNT\s*=\s*28;',reg)
assert count, 'Missing registry count or tessellation baseline'
keys=[]
for family in ('MULTILAYER','SHAPES','LINES','SINGLE','ART'):
    match=re.search(r'String\[\] '+family+r'\s*=\s*\{(.*?)\};',reg,re.S)
    assert match, 'Missing thumbnail family '+family
    keys.extend(re.findall(r'"([^"]+)"',match.group(1)))
assert len(keys)==int(count.group(1)) and len(keys)==len(set(keys)), 'Incorrect registry count or duplicate thumbnail keys'
baseline=(p/'tests/thumbnail-identities-561.txt').read_text().splitlines()
assert len(baseline)==81 and len(set(baseline))==81, 'Baseline inventory corrupted'
assert set(baseline)<=set(keys), 'Existing thumbnail identities missing: '+str(sorted(set(baseline)-set(keys)))
assert 'multilayer.leaf_vein' not in reg and 'multilayer.lace' not in reg
assert info['gate'] in (p/'BUILD_ON_NAS.sh').read_text()
assert info['gate'] in (p/'.github/workflows/verify.yml').read_text()
assert re.search(r'\bversionCode\s+'+str(info['versionCode'])+r'\b',(p/'app/build.gradle').read_text())
print('PASS RC166 picker retirement, safe variant restore, current thumbnail registry, version and NAS/CI wiring')

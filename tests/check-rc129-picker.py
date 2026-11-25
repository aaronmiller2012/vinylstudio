#!/usr/bin/env python3
"""Static UI wiring gate: new picker is UI-only, stable engine identifiers stay unchanged."""
from pathlib import Path
import json, re
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
p=(r/'app/src/main/java/com/halftonestudio/StudioStylePreview.java').read_text()
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=407
assert meta['gate'] in ('tests/run-rc129-picker-gate.sh',
                        'tests/run-rc137-quality-gate.sh',
                        'tests/run-rc138-crop-lines-gate.sh',
                        'tests/run-rc140-assembly-guide-gate.sh',
                        'tests/run-rc141-fifteen-styles-gate.sh',
                        'tests/run-rc142-style-cleanup-gate.sh','tests/run-rc143-quality-gate.sh',
                        'tests/run-rc144-registration-gate.sh','tests/run-rc145-registration-stability-gate.sh','tests/run-rc146-reliability-gate.sh','tests/run-rc147-registration-viewport-gate.sh','tests/run-rc148-registration-safety-gate.sh','tests/run-rc149-prismatic-gate.sh')
if meta['gate']=='tests/run-rc137-quality-gate.sh':
    assert 'bash tests/run-rc129-picker-gate.sh' in (r/meta['gate']).read_text(), 'Quality gate must retain picker suite'
if meta['gate']=='tests/run-rc138-crop-lines-gate.sh':
    assert 'bash tests/run-rc137-quality-gate.sh' in (r/meta['gate']).read_text(), 'Crop gate must inherit RC137'
    assert 'bash tests/run-rc129-picker-gate.sh' in (r/'tests/run-rc137-quality-gate.sh').read_text(), 'Inherited gate must retain picker suite'
if meta['gate']=='tests/run-rc140-assembly-guide-gate.sh':
    assert 'bash tests/run-rc138-crop-lines-gate.sh' in (r/meta['gate']).read_text(), 'Assembly guide gate must inherit RC138'
    assert 'bash tests/run-rc137-quality-gate.sh' in (r/'tests/run-rc138-crop-lines-gate.sh').read_text(), 'Inherited crop gate must retain RC137'
    assert 'bash tests/run-rc129-picker-gate.sh' in (r/'tests/run-rc137-quality-gate.sh').read_text(), 'Inherited quality gate must retain picker suite'
if meta['gate']=='tests/run-rc141-fifteen-styles-gate.sh':
    assert 'bash tests/run-rc140-assembly-guide-gate.sh' in (r/meta['gate']).read_text(), 'Fifteen-style gate must inherit RC140'
    assert 'bash tests/run-rc138-crop-lines-gate.sh' in (r/'tests/run-rc140-assembly-guide-gate.sh').read_text(), 'Inherited assembly guide gate must retain RC138'
    assert 'bash tests/run-rc137-quality-gate.sh' in (r/'tests/run-rc138-crop-lines-gate.sh').read_text(), 'Inherited crop gate must retain RC137'
    assert 'bash tests/run-rc129-picker-gate.sh' in (r/'tests/run-rc137-quality-gate.sh').read_text(), 'Inherited quality gate must retain picker suite'
if meta['gate']=='tests/run-rc142-style-cleanup-gate.sh':
    assert 'bash tests/run-rc140-assembly-guide-gate.sh' in (r/meta['gate']).read_text(), 'Style cleanup gate must inherit RC140'
    assert 'bash tests/run-rc138-crop-lines-gate.sh' in (r/'tests/run-rc140-assembly-guide-gate.sh').read_text(), 'Inherited assembly guide gate must retain RC138'
    assert 'bash tests/run-rc137-quality-gate.sh' in (r/'tests/run-rc138-crop-lines-gate.sh').read_text(), 'Inherited crop gate must retain RC137'
    assert 'bash tests/run-rc129-picker-gate.sh' in (r/'tests/run-rc137-quality-gate.sh').read_text(), 'Inherited quality gate must retain picker suite'
assert 'showCompactStylePicker("All")' in a
for category in ['Multilayer','Tessellation','Shapes','Lines','Single Line','Art techniques']:
    assert f'showCompactStylePicker("{category}")' in a
assert 'final String[] groups={"All","Multilayer","Tessellation","Shapes","Lines","Single Line","Art techniques"};' in a
assert 'return widthDp<345?2:widthDp>=720?4:3;' in a
assert 'getResources().getConfiguration().fontScale>=1.35f' in a
assert 'fillCompactPickerGrid' in a and 'setOnClickListener(v->choice.activate.run())' in a
assert 'ArrayList<PickerChoice> collectStylePickerChoices' in a
assert 'Math.max' not in a[a.index('private int stylePickerColumns()'):a.index('private View compactPickerTile')]
assert 'new StudioStylePreview(this,choice.previewKind)' in a
for style in ['Bottle Caps','Fish Scales','Offset Cells','Layered Cells','Tessellation','Banknote Engraving','Linocut']:
    assert style in a
# Previews are mapped by exact UI keys, never by substring guesses.
registry=(r/'app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java').read_text()
assert 'kind.contains(' not in p
assert 'StudioThumbnailRegistry.isKnown(key)' in p
assert 'StudioThumbnailRegistry.newStyle(id)' in a
assert 'StudioThumbnailRegistry.shape(id)' in a
assert 'StudioThumbnailRegistry.line(id)' in a
assert 'StudioThumbnailRegistry.single(line)' in a
assert 'TESSELLATION_COUNT = 22' in registry
assert 'stylePickerSection(' in a # historical method retained for compatibility
assert 'setMinimumHeight(dp(114))' in a
assert 'chip.setMinHeight(dp(48))' in a
assert 'setContentDescription(choice.name' in a
assert 'newStyleId==style' in a
assert 'style==VinylEngine.SQUARE' not in a # no unintended hardcoded redirects
print('PASS RC129 compact three-wide picker, categories, thumbnails and callback wiring')

if meta['gate']=='tests/run-rc143-quality-gate.sh':
    assert 'bash tests/run-rc142-style-cleanup-gate.sh' in (r/meta['gate']).read_text()

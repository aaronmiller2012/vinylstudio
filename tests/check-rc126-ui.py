#!/usr/bin/env python3
from pathlib import Path
import json, re
p=Path(__file__).resolve().parents[1]
s=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
t=(p/'app/src/main/java/com/halftonestudio/StudioTheme.java').read_text()
meta=json.loads((p/'tests/release-info.json').read_text())
assert meta['versionCode']>=406 and meta['gate'].startswith('tests/run-')
assert 'showCompactStylePicker' in s and 'fillCompactPickerGrid' in s
assert 'private void stylePickerSection(' in s and 'private View styleIconPreview(' in s
assert 'for(int index=0;index<PRIMARY_SINGLE_LINES.length;index++)' in s
assert 'VinylStyleSuite.BANKNOTE' in s
assert 'private void styleTextRow(' not in s
assert 'if(activeWorkspace==2 || activeWorkspace==3 || activeWorkspace==4)return;' in s
assert 'mountingSurfaceChoices(paper)' in s and 'mountingSurfaceChoices(b)' in s
assert 'private void mountingSurfaceChoices(' in s
assert 'dp(80)' in s and 'setMinimumHeight(dp(64))' in s
assert 'private void confirmRemoveNewStyleLayer(' in s
assert 'LinearLayout cardActions=horizontal(card)' in s
assert 'newStyleSelectedLayer=newStyleSelectedLayer==index?-1:index' in s
assert 'VinylStyleSuite.TESSELLATION_MOTIF' in s
assert 'widthDp>=600;' in s
assert 'static final int H_PILL = 48;' in t
assert f"versionCode {meta['versionCode']}" in (p/'app/build.gradle').read_text()
print('PASS RC126 drawer/search, selected states, visible Adjust, mounting colors, safe layer actions')

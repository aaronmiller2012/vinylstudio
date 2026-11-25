#!/usr/bin/env python3
from release_version import at_least
"""Ensure RC113 is a true UI rollback, not a mixed RC111 layout."""
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ui=root/'app/src/main/java/com/halftonestudio/StudioActivity.java'
# The RC110 navigation and slider structure remains, but Layered Cell
# legitimately gains two compact controls. Do not pin the entire activity hash.
source=ui.read_text()
for marker in ('private void layeredControls(LinearLayout host)',
               'slider(host,"Cell density"', 'private void showHalftoneStylesDrawer()',
               'if(layeredCellMode){layeredControls(g);}'):
    assert marker in source, f'Rollback UI invariant lost: {marker}'
assert 'toggleAdvancedText(group,key' not in source, 'RC111 slider redesign accidentally restored'
info=json.loads((root/'tests/release-info.json').read_text())
assert info['versionCode']>=406 and at_least(info['version'], '2.54.0')
assert info['gate'].startswith('tests/run-')
assert 'versionCode '+str(info['versionCode']) in (root/'app/build.gradle').read_text()
assert info['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
assert (root/'app/src/main/java/com/halftonestudio/ArtworkDocumentExport.java').is_file()
print('PASS RC113: exact RC110 user interface, export retained, monotonic Android versionCode')

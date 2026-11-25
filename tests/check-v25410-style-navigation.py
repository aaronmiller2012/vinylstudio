#!/usr/bin/env python3
from release_version import at_least
"""v2.54.10: every new engine appears once, in the requested child picker."""
from pathlib import Path
import json, re
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
head=s[s.index('    private void showUnifiedStyleDrawer()'):s.index('    private void activateNewStyle(int id){')]
assert 'styleDrawerRow(list,"Art Styles"' not in head
assert 'showNewArtStylePickerDialog' not in s
assert head.count('styleDrawerRow(')==5
multi=s[s.index('    private void showMultilayerPickerDialog()'):s.index('    private void stylePickerBack(')]
single=s[s.index('    private void showSingleLinePickerDialog()'):s.index('    /** Sample previews use the SAME mathematical carrier')]
assert 'int[] ids={6,8,9,10,18}' in multi # stable legacy IDs
assert 'final int[] rowSizes={3,2,2}' in single # unchanged 7 previews
new_multis={'LOW_POLY','GLASS','TOPOGRAPHIC'}
new_techniques={'TSP','LINOCUT','TRUCHET','RIDGE'}
for name in new_multis:
    assert 'VinylStyleSuite.'+name in multi, name
    assert 'VinylStyleSuite.'+name not in single, name
techniques=s[s.index('    private void showArtTechniquesDialog()'):s.index('    /** Sample previews use the SAME mathematical carrier')]
for name in new_techniques:
    assert 'VinylStyleSuite.'+name in techniques, name
    assert 'VinylStyleSuite.'+name not in multi, name
assert 'VinylStyleSuite.TEXT' not in techniques, 'Retired Word styles must remain hidden'
assert multi.count('activateNewStyle(styleId)')==1
assert techniques.count('activateNewStyle(id)')==1
assert 'activateNewStyle(int id)' in s and 'newStyleId=id;' in s
assert 'newStylePaletteMemory[id].clone()' in s
for key in ['newStyleColorsPanel()', 'newStyleLayersPanel()', 'startNewStyleRender(draft)', 'exportNewStyle(uri)']:
    assert key in s
assert 'newStyleSelectedLayer' in s
meta=json.loads((root/'tests/release-info.json').read_text()); gradle=(root/'app/build.gradle').read_text()
assert meta['versionCode']>=323 and at_least(meta['version'], '2.54.0')
assert meta['versionName'] in gradle
assert meta['gate'] in Path('BUILD_ON_NAS.sh').read_text()
assert meta['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
print('PASS: three Multilayer styles, four art techniques, legacy single-line picker, stable IDs and restored-workspace navigation')

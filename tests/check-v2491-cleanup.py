#!/usr/bin/env python3
"""v2.49.1: live UI owns every shared control; project IDs remain compatible."""
from pathlib import Path
import re
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
b=(r/'app/build.gradle').read_text()
assert "versionCode 300" in b and '2.52.1-line-visibility-rc22' in b
removed=['layerStepPanel','imagePanel','layerPanel','colorPanel','colorRegularLayersPanel',
'colorTonalLayersPanel','colorLayeredPanel','colorTessLayersPanel','showLegacyStyles',
'addAlternateStyleControls','saveStyleFavorite','restoreStyleFavorite','exportPanel']
for item in removed:
 assert not re.search(r'private\s+void\s+'+item+r'\s*\(',a),item
assert 'showUnifiedStyleDrawer()' in a
assert 'case 2:colorPaletteOnlyPanel();break;' in a
assert 'case 3:unifiedLayersPanel();break;' in a
adjust=a[a.index('private void halftonePanel(){'):a.index('private void chooseChannelLineStyle(')]
assert '"Auto fit SVGs"' not in adjust
assert '"Reset best settings"' in adjust
layer=a[a.index('private void unifiedLayersPanel(){'):a.index('private void selectUnifiedLayer(')]
for control in ['"Cells across image"','"Tile detail"','"Minimum cut feature · mm"','tonalDetailControl(overview)']:
 assert control not in layer,control
assert 'if(selected>=0)action(overview,"Show complete artwork"' in layer
assert '"Color wheel"' in layer
check=a[a.index('private void cutPanel(){'):a.index('private void levelsPanel()')]
assert '"Minimum cut width"' in check
assert '"Layered Cell minimum feature · mm"' in check
assert '"Auto fit cuts"' in check
assert 'openWorkspaceSubtool(5,1)' in check
assert 'openWorkspaceSubtool(4,1)' not in check
for fragment in ['out.put("schema",14)', 'out.put("tessellationMode"', 'out.put("layeredCellMode"']:
 assert fragment in a
print('PASS v2.49.1: dead UI removed, singleton global controls, Export navigation, legacy format preserved')

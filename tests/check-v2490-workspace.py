#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
a=s[s.index('private static final String[] WORKSPACE_NAMES='):s.index('private boolean boundaryPanelSelected=')]
assert '"Prepare","Shapes","Adjust","Colors","Layers","Export"' in a
assert '"✂", "◇", "☷", "◉", "▤", "⇧"' in a
assert '{"Import","Crop","Snip","Lighting","Size"}' in a
assert '{"Boundary","Marks","Cut Check","Export"}' in a
assert 'if(next==1)' in s and 'showUnifiedStyleDrawer();' in s
for phrase in ('else if(next==2){activeSubtoolPosition=0;setToolInternal(1)',
               'else if(next==3){activeSubtoolPosition=0;setToolInternal(2)',
               'else if(next==4){activeSubtoolPosition=0;setToolInternal(3)'):
 assert phrase in s,phrase
assert 'private final int[] workspaceDockDp={208,225,225,210,300,210}' in s
assert 'int workspace=t==2?3:t==3?4:' in s
assert '(t==1||t==4||t==9)?2:0;' in s
assert 'if(activeWorkspace==3 || activeWorkspace==4)return;' in s
panel=s[s.index('private void halftonePanel(){'):s.index('private void shapeControls(){') if 'private void shapeControls(){' in s else s.index('private void applyPalette(')]
assert 'Vinyl quality · ' not in panel
assert 'PROFILES.length' not in panel
adjust=s[s.index('private void layeredControls('):s.index('private void applyLayeredLook(')]
assert '"Portrait",()->applyLayeredLook' not in adjust
assert '"Art mosaic",()->applyLayeredLook' not in adjust
assert '"Easy weed",()->applyLayeredLook' not in adjust
assert '"Suggest photo colors' not in adjust
colors=s[s.index('private void colorPaletteOnlyPanel(){'):s.index('private int[] presetTessSwatches(')]
assert colors.index('action(suggestion,"Suggest photo colors"') < colors.index('section(palettes,"Vinyl palettes")')
assert 'if(tonalColorsReady)' in s and 'Preparing photo-derived layer colors' in s
assert 'out.put("schema",14)' in s
print('PASS: v2.49.0 six-workspace routing, quality and layered-look cleanup, colors suggestions and schema stability')

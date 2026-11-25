from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
src=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
g=Path('app/build.gradle').read_text()
assert version_code_matches(g) and version_name_matches(g)
assert 'private void expandToolSheet()' in src and 'private void collapseToolSheet()' in src
assert 'expandToolSheet();' in src and 'if(panelOpen){collapseToolSheet();return;}' in src
assert 'sheetContent.addView(rail,0' in src and 'selectedSettingPills.put(key,next)' in src
assert 'activeSubtoolPosition=position;' in src
assert 'TextView more=action(sheetHead' not in src
assert 'sheetRoot.addView(compactSliderRow' not in src
assert 'handler.post(this::showFirstRunTour)' not in src
assert 'addContextTip();' not in src
assert 'private void snipPanel()' in src
p=src[src.index('private void snipPanel()'):src.index('private void applySnip()')]
assert 'this::pick' in p and 'Background outside snip' not in p  # v2.48 removes snip background controls
assert all(k in p for k in ('"Remove inside"','"Keep inside"','"Close points"','"Apply snip"','"Discard outline"','"Clear all"'))
assert all(k not in p for k in ('"Undo"','"Redo"','"Undo point"','"Undo last snip"','"Width · quick adjustment"'))
assert 'performHistoryAction(false)' in src and 'performHistoryAction(true)' in src
assert 'status.setSingleLine(true)' in src
assert 'editorArea=new FrameLayout(this)' in src and 'FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM)' in src
assert 'syncCanvasDockSpace(lp.height)' in src and 'panelOpen?sheetMaxHeight():dp(DOCK_COLLAPSED_DP)' in src
assert 'new PatternSample(style)' in src and 'VinylEngine.generate(' in src
print('PASS: V2.29 all four workspace pills open controls, no More, no redundant snip undo, preserved vector engine')

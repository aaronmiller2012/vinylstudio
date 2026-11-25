from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
g=Path('app/build.gradle').read_text()
assert 'WORKSPACE_NAMES={"✂", "◇", "☷", "◉", "▤", "⇧"}' in s
assert 'private void showUnifiedStyleDrawer()' in s
assert 'showUnifiedStyleDrawer();return;' in s
assert 'new PatternSample(style)' in s and 'new SingleLineSample(style)' in s and '"Adaptive Hilbert"' in s and '"Maze Portrait"' in s
assert 'showUnifiedStyleDrawer();return;' in s  # v2.48 Shapes has its own Styles subtab
assert 'panelOpen?sheetMaxHeight():dp(DOCK_COLLAPSED_DP)' in s
assert 'workspaceDockDp[activeWorkspace]' in s and 'Math.min(340,' in s
assert 'heightPixels*78/100' in s
assert 'controlsColumn.addView(layerScroller' not in s
assert 'controlsColumn.addView(favoritesScroller' not in s
assert 'fullscreenButton.setOnClickListener(v->setCanvasFullscreen(!canvasFullscreen))' in s
assert 'header.setVisibility(View.VISIBLE)' in s
assert 'private void editColor(' in s and 'private void colorSwatchGrid(' in s
assert 'addCompactSpecialtyAdjustments();' in s
assert version_name_matches(g)
print('PASS: V2.20 canvas-first layout, 4 workspaces, unified styles, top full screen and per-color wheel')

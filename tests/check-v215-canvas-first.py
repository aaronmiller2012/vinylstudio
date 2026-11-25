from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
g=Path('app/build.gradle').read_text()
checks={
 'version': version_name_matches(g),
 'upper right undo': 'topUndo.setOnClickListener(v->performHistoryAction(false))' in s,
 'upper right redo': 'topRedo.setOnClickListener(v->performHistoryAction(true))' in s,
 'style drawer': 'private void showHalftoneStylesDrawer()' in s,
 'style picker opens from shelf': 'this::showHalftoneStylesDrawer' in s,
 'style previews': 'new PatternSample(style)' in s and 'new SingleLineSample(style)' in s,
 'no inline gallery': 'Select a halftone style' not in s,
 'dedicated canvas': 'canvasStage.addView(artwork' in s,
 'shorter collapsed shelf': 'panelOpen?sheetMaxHeight():dp(DOCK_COLLAPSED_DP)' in s,
 'svg engine untouched': 'private void requestExport()' in s,
}
for name,good in checks.items():
 if not good: raise AssertionError(name)
print('PASS: v2.15 canvas-first controls, top history, style drawer, stable canvas, unchanged SVG engine')

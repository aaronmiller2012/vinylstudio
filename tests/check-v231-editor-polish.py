from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
s=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
g=(Path(__file__).resolve().parents[1]/'app/build.gradle').read_text()
assert version_code_matches(g) and version_name_matches(g)
assert 'showProjectMenu(overflow)' in s and 'showProjectMenu(View anchor)' in s
assert 'header.addView(exitProject' not in s and 'confirmExitProject()' in s
assert 'editorSubtitle=text("New project",13' in s and 'renderProgress.setIndeterminate(true)' in s
assert 'private static final String[] WORKSPACE_NAMES={"✂", "◇", "☷", "◉", "▤", "⇧"}' in s
assert 'private static final int DOCK_COLLAPSED_DP=57' in s
assert 'subtoolScroller.setVisibility(View.GONE)' in s and 'subtoolScroller.setVisibility(View.VISIBLE)' in s
assert 'LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1)' in s
assert '"Import","Crop","Snip","Lighting","Size"' in s and '"Adjust"' in s
assert 'case 2:colorPaletteOnlyPanel();break;' in s and 'case 3:unifiedLayersPanel();break;' in s
assert 'else showFinishedSizeDialog();' in s
assert 'renderBusy=busy' in s and 'updateEditorSubtitle()' in s
assert 'private void refreshChannelOverlay()' in s and 'layer.enabled=!layer.enabled' in s
assert 'private void showPreviewMenu(View anchor)' in s and '"Overlay photo"' in s
assert 'if(zoom>1.05f)resetZoom();else' not in s
assert 'if(tool!=8&&draggedMark<0){holdCompare=true;' in s
assert 'workspaceDockHeight' in s and 'dragStartHeight' in s
assert 'styleDrawerRow(list,"CMYK"' in s and 'new PatternSample(VinylEngine.LINE)' in s
assert 'private void requestRender()' in s and 'VinylEngine.generate(' in s
print('PASS V2.31: editor header/menu, compact tabs, context tools, channel previews, fit/compare, resizable dock')

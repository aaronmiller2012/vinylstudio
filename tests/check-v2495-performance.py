#!/usr/bin/env python3
"""Performance release: in-place count bindings, main-thread safety, no file-schema edits."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'versionCode 300' in (r/'app/build.gradle').read_text()
assert '2.52.1-line-visibility-rc22' in (r/'app/build.gradle').read_text()
assert 'private void updateVisibleLayerCutLabels()' in s
assert 'sheetContent.findViewWithTag(CUT_COUNT_TAG_PREFIX+i)' in s
assert 'cutsView.setTag(CUT_COUNT_TAG_PREFIX+i)' in s
assert 'if(!label.contentEquals(value.getText()))value.setText(label)' in s
assert 'final int[] completedCounts=draft?null:LayerCutCounts.standard(out);' in s
assert 'standardLayerCutCounts=completedCounts;' in s
assert 'return standardCountSerial==renderedSerial?standardLayerCutCounts:null;' in s
assert 'if(!sliderDragging)invalidateToolPanels();' in s
assert 'sliderDragging=false;invalidateToolPanels();requestRender();' in s
assert 'android.os.Trace.beginSection("VinylStudio.PanelBuild")' in s
assert 'panelTool="+t+" buildMs=' in s
# These completion branches must refresh text, not rebuild the entire workspace.
start=s.index('final Bitmap preview=display;')
end=s.index('private int alternateExportToken=',start)
assert 'updateVisibleLayerCutLabels();' in s[start:end]
assert 'refreshShelfPanel();' not in s[start:end]
start=s.index('private void render(){')
end=s.index('private static int[] downsampleArgb',start)
assert 'updateVisibleLayerCutLabels();' in s[start:end]
assert 'refreshShelfPanel();' not in s[start:end]
assert 'out.put("schema",14)' in s
assert 'VinylQuickStartGuide.isFirstInstall' in s
print('PASS v2.49.5 performance: no layer-card rebuild on render, generation-safe counts and panel tracing')

#!/usr/bin/env python3
"""Regression: former section-decorator was hiding Colors / Layers behind navigation pills."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
meta=(root/'tests/release-info.json').read_text()
assert '2.51.8-workflow-audit-rc19' in meta
colors=s[s.index('    private void colorPaletteOnlyPanel(){'):s.index('    private int[] presetTessSwatches(')]
assert colors.index('section(b,"Mounting surface")') < colors.index('section(presets,"Vinyl palettes')
assert 'action(papers,"White"' in colors and 'action(papers,"Black"' in colors
layers=s[s.index('    private void unifiedLayersPanel(){'):s.index('    private void selectUnifiedLayer(')]
assert 'colorSwatchGrid(overview' in layers
assert 'layerCountPicker(overview' in layers
assert 'section(overview,"Layers · "' not in layers
assert 'section(stack,"Stack controls")' not in layers
assert '"Move up"' in layers and '"Move down"' in layers
assert '"Remove selected layer"' in layers
func=s[s.index('    private void decorateCollapsibleSections(){'):s.index('    private boolean hasDirectSlider(')]
assert 'if(activeWorkspace==3 || activeWorkspace==4)return;' in func
assert func.index('if(activeWorkspace==3 || activeWorkspace==4)return;') < func.index('p.removeViewAt(0);')
# Verify the guard covers both the panel build and refresh construction paths.
assert s.count('decorateCollapsibleSections();')>=2
assert 'case 2:colorPaletteOnlyPanel();break;' in s
assert 'case 3:unifiedLayersPanel();break;' in s
# No geometry engine or saved-project schema alteration.
assert 'out.put("schema",14)' in s
print('PASS v2.48.5: Colors and Layers are always-visible pages, no stack submenu, reorder available in layer editor')

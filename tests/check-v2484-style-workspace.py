#!/usr/bin/env python3
"""v2.48.4 UI contract; legacy generator and project enum IDs remain stable."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
base=(root/'app/src/main/java/com/halftonestudio/ContinuousLineModes.java').read_text()
meta=(root/'tests/release-info.json').read_text()
# Test compatibility with the current packaged release rather than a frozen
# versionName from the previous UI-only release.
import json
version=json.loads(meta)['versionName']
assert version in (root/'app/build.gradle').read_text()
style=s[s.index('private void showUnifiedStyleDrawer()'):s.index('private void showShapePickerDialog()')]
assert all(f'styleDrawerRow(list,"{n}"' in style for n in ['Shapes','Lines','Single Line'])
assert 'showShapePickerDialog()' in style and 'showLinesPickerDialog()' in style
assert 'showSingleLinePickerDialog()' in style
names=re.search(r'private static final String\[\] SINGLE_LINE_NAMES=\{(.*?)\};',style,re.S).group(1)
assert len(re.findall(r'"[^"]+"',names))==7
assert all(f'ContinuousLineModes.LineStyle.{v}' in style for v in ['SPIRAL','ADAPTIVE_HILBERT','GOSPER','PEANO','WUNDERLICH','MOORE','COIL'])
assert 'new SingleLineSample(style)' in style
assert 'SpaceFillingCurves.generate(o)' in style
assert 'o.depth=2;' in style
assert 'private void showLegacyStyles()' not in s and 'COIL, MEURTHE' in base
assert 'private void singleLineVariations(LinearLayout host)' in s and 'Spiral variation:' in s and 'Raster variation:' not in s
assert 'Meurthe · legacy curve' not in style and 'Tonal Spirograph · legacy mode' not in style
assert 'else if(idx<=5)continuousStyle=ContinuousLineModes.LineStyle.values()[idx];' in s
lighting=s[s.index('private void levelsPanel()'):s.index('private void requestRender()')]
assert all(f'"{name}"' not in lighting for name in ['Lighting · photo adjustments','Before / after','Snip'])
assert '"Exposure (stops)"' in lighting and '"Auto levels"' in lighting and '"Reset"' in lighting
colors=s[s.index('private void colorPaletteOnlyPanel()'):s.index('private int[] presetTessSwatches(')]
assert colors.index('section(b,"Mounting surface")')<colors.index('section(presets,"Vinyl palettes')
assert 'for(int slot=0;slot<=VinylPalettes.NAMES.length' in colors
layers=s[s.index('private void unifiedLayersPanel()'):s.index('private void selectUnifiedLayer(')]
assert '"All layers · "' not in layers and '"Physical vinyl stack · bottom to top"' not in layers
assert 'colorSwatchGrid(overview' in layers and '"Color wheel"' in layers
assert '"Minimum cut width"' in s[s.index('private void cutPanel()'):s.index('private void levelsPanel()')]
assert 'enum LineStyle { SPIRAL, CONCENTRIC_RINGS, SINE_RASTER, CONTOUR_HATCH, ADAPTIVE_HILBERT, TONAL_SPIROGRAPH, GOSPER, PEANO, SIERPINSKI, WUNDERLICH, MOORE, COIL, MEURTHE }' in base
assert 'out.put("schema",14)' in s
print('PASS v2.48.4: grouped 3-drawer styles, 7 primary Single Line previews, legacy IDs, lighting/colors/layers cleanup and SVG schema')

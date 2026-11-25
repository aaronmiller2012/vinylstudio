#!/usr/bin/env python3
"""Verify v2.48.7 single entry-point, back behavior, and legacy compatibility."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]
s=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
root=s[s.index('    private void showUnifiedStyleDrawer()'):s.index('    /** Saved-project art-engine IDs')]
sequence=['styleDrawerRow(list,"CMYK",','styleDrawerRow(list,"Multilayer",',
          'styleDrawerRow(list,"Shapes",','styleDrawerRow(list,"Lines",',
          'styleDrawerRow(list,"Single Line",']
positions=[root.index(x) for x in sequence]
assert positions==sorted(positions), positions
assert 'Other styles · saved-project compatibility' not in root
assert 'showMultilayerPickerDialog()' in root
multi=s[s.index('    private void showMultilayerPickerDialog()'):s.index('    private void stylePickerBack(')]
assert 'int[] ids={6,8,9,10,18}' in multi
for name in ['Multilayer tonal stencil','Adaptive Voronoi mosaic','Grid Cell Portrait','Layered Cell Portrait']:
    assert name in multi
lines=s[s.index('    private void showLinesPickerDialog()'):s.index('    /** User-facing single-line styles.')]
assert all('VinylEngine.'+name in lines for name in ['LINE','STRAIGHT','CONTOUR','WAVE','HATCH','CROSSHATCH','ZIGZAG','BROKEN'])
assert all('VinylEngine.'+name not in lines for name in ['BEADED','FADE','RINGS','RAYS'])
assert 'private void showLegacyStyles()' not in s and 'VinylEngine.BEADED' in s # historic engine codes retained
assert 'styleDialogRoot("Shapes",dialog,true)' in s
for d in ['Multilayer','Lines','Single Line','Shapes']:
    assert 'styleDialogRoot("'+d+'",dialog,true)' in s
sheet=s[s.index('    private void stylePickerBack('):s.index('    private void styleTextRow(')]
assert 'showUnifiedStyleDrawer()' in sheet
assert 'KEYCODE_BACK' in sheet and 'ACTION_UP' in sheet
assert 'setCanceledOnTouchOutside(true)' in sheet
assert 'dialog.dismiss()' in sheet and 'getRawY()' in sheet
assert 'heightPixels*.94f' in sheet
assert 'ScrollView' in s[s.index('    private void showUnifiedStyleDrawer()'):s.index('    private void selectSpaceCurve(')]
assert 'out.put("schema",14)' in s
print('PASS v2.48.7: ordered CMYK/multilayer/shapes/lines/single line, hidden legacy choices, Back and swipe sheet navigation')

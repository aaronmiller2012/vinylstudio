#!/usr/bin/env python3
"""User-requested retired tiles are absent from all reachable Single Line selection paths."""
from pathlib import Path
import re
base=Path(__file__).resolve().parents[1]
s=(base/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
engine=(base/'app/src/main/java/com/halftonestudio/ContinuousLineModes.java').read_text()
section=s[s.index('private static final ContinuousLineModes.LineStyle[] PRIMARY_SINGLE_LINES='):s.index('private final class SingleLineSample extends View')]
styles=re.search(r'PRIMARY_SINGLE_LINES=\{(.*?)\};',section,re.S).group(1)
names=re.search(r'SINGLE_LINE_NAMES=\{(.*?)\};',section,re.S).group(1)
assert len(re.findall(r'LineStyle\.([A-Z_]+)',styles))==7
assert len(re.findall(r'"[^"]+"',names))==7
for removed in ['SINE_RASTER','SIERPINSKI']:
    assert 'LineStyle.'+removed not in styles, removed
    assert removed in engine, 'Old project enum missing: '+removed
for removed in ['"Sine Raster"','"Sierpiński"']:
    assert removed not in names
assert 'final int[] rowSizes={3,2,2}' in section
assert 'Raster variation:' not in s
assert 'chooseSpecialtyStyle()' not in s
assert 'private void showProjectMenu()' not in s
assert 'new IconButton(' not in s
assert 'showProjectMenu(overflow)' in s
assert 'showSingleLinePickerDialog();' in s
print('PASS: 7 selectable single-line styles, stable legacy enum IDs, balanced grid, no dead duplicate project sheet')

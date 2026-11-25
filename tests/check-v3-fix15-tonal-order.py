#!/usr/bin/env python3
"""Static integration guard for source-tone palette mapping, not physical L1 order."""
from pathlib import Path
s=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'private boolean applyTonalSourcePaletteColors(int[] picked,int hue)' in s
assert 'TonalPhotoPaletteOrder.mapToSource(' in s
assert 'if(!applyTonalSourcePaletteColors(colors,tonalHueDegrees))return;' in s
assert 'if(!applyTonalSourcePaletteColors(' in s
assert '"photo".equals(key)' in s and 'Arrays.fill(tonalColorOverrides,0);' in s
assert 'tonalMatchMyVinyl=false;' in s
assert 'private boolean applyTonalPhysicalColors(int[] physical,int hue)' in s
assert 'if(!applyTonalPhysicalColors(java.util.Arrays.copyOf(mapped,tonalLayerCount),saved.hueDegrees))return;' in s
assert 'TonalPhotoPaletteOrder.sortDarkToLight(' in s
print('PASS Fix15 UI: presets source-tone mapped, photo reset, explicit saved physical palettes retained')

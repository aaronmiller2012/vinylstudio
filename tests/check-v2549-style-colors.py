#!/usr/bin/env python3
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
for needle in ['newStyleCaptureRenderedColors()', 'newStyleDisplayedColor(int slot)', 'newStyleSwatchRow(', 'editNewStyleColor(int slot)', 'newStyleLayersPanel()', 'newStylePaletteMemory', 'out.put("newStylePaletteMemory",memories)', 'newStyleColors=newStylePaletteMemory[id].clone()', 'NewStyleColorSlots.fixedPalette(', 'config.leadColor=newStyleColors[0]']:
 assert needle in s,needle
assert 'config.leadColor=tessColors[0]' not in s
assert 'editTessColor(index)' not in s[s.index('private void newStyleColorsPanel()'):s.index('private void showMultilayerPickerDialog()')]
m=json.loads((r/'tests/release-info.json').read_text());gradle=(r/'app/build.gradle').read_text()
assert str(m['versionCode']) in gradle and m['versionName'] in gradle
print('PASS eight-style UI and persistence source wiring')

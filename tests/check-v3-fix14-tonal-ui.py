#!/usr/bin/env python3
"""Source-level guard: every tonal UI palette is physical-stack mapped."""
from pathlib import Path
s = (Path(__file__).resolve().parents[1]/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
strings=(Path(__file__).resolve().parents[1]/'app/src/main/res/values/strings.xml').read_text()
assert '"Prepare","Styles","Adjust","Colors","Layers","Export"' in s
assert 'showCompactStylePicker("Styles")' in s
from xml.etree import ElementTree as ET
resources = {e.get('name'): e.text or '' for e in ET.fromstring(strings).findall('string')}
assert 'Open Styles to choose a rendering family.' in resources['guide_body_2']
assert 'tonalDisplayedColors()' in s
assert 'return TonalPaletteMapping.display(rendered,sources,tonalColorOverrides,' in s
assert 'TonalPaletteMapping.fromPhysical(tonalColorOverrides,physical,sources,hue)' in s
assert 'if(tonalStencilMode)return tonalDisplayedColors();' in s
assert 'if(tonalStencilMode){\n            int[] colors=new int[8];int[] displayed=tonalDisplayedColors();' in s
assert '!tonalStencilMode && !newStyleActive() && !tessellationMode' in s
assert 'currentPaletteBases(),newStyleActive()||tonalStencilMode?0:currentVinylHue()' in s
assert 'if(!applyTonalSourcePaletteColors(' in s  # Built-in presets follow source-tone order
assert 'if(!applyTonalPhysicalColors(java.util.Arrays.copyOf(mapped,tonalLayerCount),saved.hueDegrees))return;' in s  # Named user palettes keep physical order
assert 'if(tonalStencilMode&&!tonalReadyForPaletteEdit()){' in s
assert 'for(int i=0;i<tonalLayerCount;i++)tonalColorOverrides[i]=mapped[i]' not in s
assert '"Shapes","Adjust"' not in s
print('PASS Fix14 UI: Styles tab, tonal rendered swatch single source, source-ID palette mapping and named-palette classification')

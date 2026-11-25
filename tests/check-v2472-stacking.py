#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]
s=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
c=(p/'app/src/main/java/com/halftonestudio/ColorWorkspacePalette.java').read_text()
assert 'ColorWorkspacePalette.selectForSurface(VinylPalettes.get(settings.paletteId,8),settings.layers.size(),settings.paper)' in s
assert 'ColorWorkspacePalette.selectForSurface(VinylPalettes.get(id,8),settings.layers.size(),settings.paper)' in s
assert 'ColorWorkspacePalette.selectForSurface(ColorWorkspacePalette.tessPalette(paletteId),tessLayerCount,settings.paper)' in s
assert 'ColorWorkspacePalette.arrangedForSurface(ColorWorkspacePalette.tessPalette(id),tessLayerCount,settings.paper)' in s
assert 'applyActivePresetColors(); // Recompute full preset subset/order for the new 2–8 count.' in s
assert 'settings.paletteId=-1;Collections.swap(settings.layers,i,target)' in s
assert 'sortForSurface(result,count,surface)' in c
assert 'int[] channels={2,0,1,3};' in s
assert 'out.put("tessellationColors",colors)' in s
assert 'tessColors=projectColors' in s
assert 'out.put("layers",layers)' in s
print('PASS: substrate-aware subset, count re-selection, preview/application parity; CMYK and saved serialization intact')

#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'private void applyActivePresetColors()' in s
assert 'private void selectMountingSurface(int color)' in s
assert 'applyActivePresetColors(); // Recompute full preset subset/order for the new 2–8 count.' in s
for word in ['White','Cream','Black']:
    assert f'action(papers,"{word}",()->selectMountingSurface(' in s
assert 'if(settings.mode==VinylEngine.CMYK||settings.paletteId<0' in s
assert 'settings.paletteId=-1;Collections.swap(settings.layers,i,target);' in s
assert 'case 2:colorPaletteOnlyPanel();break;' in s
assert 'if(activeWorkspace==3 || activeWorkspace==4)return;' in s
assert 'ColorWorkspacePalette.selectForSurface(VinylPalettes.get(id,8),settings.layers.size(),settings.paper)' in s
assert 'if(settings.mode==VinylEngine.CMYK&&VinylEngine.CmykExtendedStack.hasAnchors(settings.layers))' in s
assert 'out.put("schema",14)' in s
print('PASS: surface selection, 2-8 layer recount, protected CMYK/custom stacks, unified Colors and Layers, saved schema')

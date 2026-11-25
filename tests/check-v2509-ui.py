#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
t=(r/'app/src/main/java/com/halftonestudio/TessellationEngine.java').read_text()
part=a[a.index('if(stippleActive()){',a.index('private void colorPaletteOnlyPanel()')):a.index('if(layeredCellMode){',a.index('private void colorPaletteOnlyPanel()'))]
assert 'for(int first=0;first<8;first+=2)' in part, 'eight swatches not displayed'
assert 'final int layer=i,swatch=tessColors[i]' in part and 'final boolean active=i<tessLayerCount' in part, 'wrong source for chips'
assert 'else editTessColor(layer);' in part and 'tessLayerCount=layer+1' in part, 'swatches not editable or activatable'
assert 'Inspect all SVG cut paths' in part, 'inspector missing from Colors'
assert 'Regenerate swatches from photo' in part, 'regenerate control missing'
assert 'if(activeWorkspace==3&&tool==2)refreshShelfPanel()' in a, 'manual edit does not refresh current Colors panel'
start=a.index('private void showStippleCutPaths(')
end=a.index('private int[] presetTessSwatches',start)
inspector=a[start:end]
assert 'canvas.drawPath(path,fill)' in inspector and 'canvas.drawPath(path,contour)' in inspector, 'no direct vector drawing'
assert 'Bitmap.createBitmap' not in inspector and 'renderTessPreview' not in inspector, 'inspector is still bitmap-backed'
assert 'private static void addCutRing(Path path,float[] xy)' in inspector
assert 'action(card,"Inspect vector cuts for this layer"' in a
assert ('tessColors[idx]=wheel.getColor();tessPalette="Custom"' in a or 'preserveManualTessColor(idx,wheel.getColor());tessPalette="Custom"' in a), 'manual color state missing'
assert 'out.put("tessColors"' in a or 'out.put("tessellationColors"' in a, 'serialized colors missing'
assert '<path fill-rule=' in t and r'mm\" height=' in t and 'swatch-' in t, 'SVG polygon export missing'
assert ('versionCode 292' in (r/'app/build.gradle').read_text() or ('versionCode 293' in (r/'app/build.gradle').read_text() or 'versionCode 294' in (r/'app/build.gradle').read_text() or ('versionCode 295' in (r/'app/build.gradle').read_text() or ('versionCode 296' in (r/'app/build.gradle').read_text() or 'versionCode 300' in (r/'app/build.gradle').read_text()))))
assert 'VinylHalftoneStudio-v2.52.1-LineVisibility-RC22.apk' in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS: 8 editable swatches, manual persistence, direct vector cut-path inspection and real SVG export')

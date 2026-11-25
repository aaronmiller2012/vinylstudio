from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
colors=s[s.index('private void colorPaletteOnlyPanel()'):s.index('private int[] presetTessSwatches')]
layer=s[s.index('layerCountPicker(overview,count,n->'):s.index('if(!cells&&!tess&&!tonal&&settings.mode')]
assert 'if(stippleActive())invalidateStipplePhotoPaletteRequest();' in layer
assert 'else refreshTessPaletteForCount();' not in layer
assert 'suggestStipplePhotoColors();' not in layer, 'Changing layer count must not auto-derive photo palette'
assert 'tessLayerCount=n;' in layer and 'tessSelectedLayer=-1' in layer
assert 'setTessBasePalette(' not in layer and 'applyTessHue(' not in layer
assert 'tessHueBaseColors' in s and 'tessHueDegrees' in s
assert 'final long paletteRequest=++stipplePhotoPaletteRequest;' in s
assert 'paletteRequest!=stipplePhotoPaletteRequest' in s
assert 'invalidateStipplePhotoPaletteRequest();\n        tessHueDegrees=PaletteHueShift.clampDegrees(degrees);' in s
assert 'invalidateStipplePhotoPaletteRequest();\n        tessHueBaseColors[index]=' in s
assert 'checkpoint();invalidateStipplePhotoPaletteRequest();\n                                tessLayerCount=layer+1;' in s
assert 'checkpoint();invalidateStipplePhotoPaletteRequest();\n                    tessLayerCount=8;' in s
assert 'invalidateStipplePhotoPaletteRequest();\n            tessHueBaseColors=projectHueBase;' in s
assert 'invalidateStipplePhotoPaletteRequest();JSONObject restored=new JSONObject' in s
assert 'out.put("tessellationHueBaseColors",hueBase);out.put("tessellationHueDegrees",tessHueDegrees);' in s
assert 'action(derived,"Regenerate swatches from photo",()->{' in colors
assert '"Photo-derived".equals(tessPalette)' in s
assert ('versionCode 295' in (r/'app/build.gradle').read_text() or ('versionCode 296' in (r/'app/build.gradle').read_text() or 'versionCode 300' in (r/'app/build.gradle').read_text()))
print('PASS palette invariant: count changes keep 8 base swatches + hue, async stale results ignored, explicit regenerate retained')

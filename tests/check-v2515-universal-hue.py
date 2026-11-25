from pathlib import Path
import re,json
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(r/'app/src/main/java/com/halftonestudio/MultilayerStencilEngine.java').read_text()
for text in [
 'if(continuousStyle==null&&!mazePortraitMode)vinylPaletteHueControl(b);',
 'private void applyStandardHue(int degrees)',
 'private void preserveManualStandardColor(int index,int color)',
 'private int tonalHueDegrees=0;',
 'options.paletteHueDegrees=requestedTonalHue;',
 'out.put("standardHueBaseColors",standardHueBase);',
 'out.put("standardHueDegrees",standardHueDegrees);',
 'out.put("tonalHueDegrees",tonalHueDegrees);',
 'json.optInt("standardHueDegrees",0)',
 'json.optInt("tonalHueDegrees",0)',
 'PaletteHueShift.rotateOne(wheel.getColor(),-tonalHueDegrees)',
 'preserveManualStandardColor(ix,c)',
 'applyCurrentVinylHue(0)',
 'private void refreshHueArtwork()',
 ]:
 assert text in s,text
assert s.index('vinylPaletteHueControl(b);')<s.index('if(tonalStencilMode){',s.index('private void colorPaletteOnlyPanel()'))
assert 'rebaseStandardHue();' in s
assert 'colors[i]=PaletteHueShift.rotateOne(colors[i],opt.paletteHueDegrees);' in e
assert e.index('buildStackOrder(')<e.index('colors[i]=PaletteHueShift.rotateOne(colors[i],opt.paletteHueDegrees);')
assert 'settings.paper=PaletteHueShift' not in s
info=json.loads((r/'tests/release-info.json').read_text())
assert info['versionCode'] in (294,295,296,297,298,300)
assert info['apk'] == 'VinylHalftoneStudio-v2.52.1-LineVisibility-RC22.apk'
b=(r/'BUILD_ON_NAS.sh').read_text()
assert info['apk'] in b
print('PASS universal hue UI: standard, CMYK, spot, tessellation, layered-cell, tonal; serialization and per-color edits')

from pathlib import Path
p=Path('app/src/main/java/com/halftonestudio')
s=(p/'StudioActivity.java').read_text()
for name in ['MultilayerStencilEngine.java','SingleLineSwirlEngine.java','ContinuousLineModes.java','ContinuousLineSvg.java']:
 assert (p/name).is_file(),name
for frag in ['showUnifiedStyleDrawer','requestAlternate()','requestAlternateExport','ALTERNATE_EXPORT','ContinuousLineModes.generatePath(prepared,style','MultilayerStencilEngine.generate','layers.svg(i)','continuousPreview','tonalDisplayBitmap']:
 assert frag in s,frag
for style in ('SPIRAL','CONCENTRIC_RINGS','SINE_RASTER','CONTOUR_HATCH'):
 assert style in (p/'ContinuousLineModes.java').read_text()
assert 'bridgeMode=false' in s
assert 'm.nextContour()' in (p/'ContinuousLineSvg.java').read_text()
print('PASS: V2.16 specialty classes, UI selection, preview, async generation, separate SVG export')

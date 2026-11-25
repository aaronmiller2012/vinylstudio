from pathlib import Path
src=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
engine=Path('app/src/main/java/com/halftonestudio/MultiColorStippleEngine.java').read_text()
tess=Path('app/src/main/java/com/halftonestudio/TessellationEngine.java').read_text()
required=['stippleSettings.overlap','stippleSettings.assignment','stippleControls(g)',
 'stippleSettings.minDotMm','stippleSettings.density','stippleSettings.enabled',
 'stippleOrder','stippleDensity','stippleMinDotMm','stippleEnabled',
 'tessellationStyle==TessellationEngine.STIPPLE','stippleHighlightCollisions',
 'fullStippleSource','o.stippleOptions.draft=false','stippleOptions.draft=true',
 'overlap-report.csv','all-swatches-grouped.svg','renderTessCollisionPreview',
 'startDragAndDrop','stippleOrderNormalize','stippleSettings.trapMm',
 'stippleSettings.minGapMm']
for token in required:
    assert token in src, 'missing app wiring '+token
for token in ['relax(pts','ERROR_DIFFUSION','SHARED','SPACED','KNOCKOUT','OVERPRINT',
              'clipRaster(', 'nearSubstrate(', 'ownership(', 'o.minDotMm']:
    assert token in engine, 'missing engine logic '+token
assert 'MultiColorStippleEngine.generate' in tess
assert 'fill-rule=\\"evenodd' in tess or 'fill-rule=\"evenodd' in tess
print('PASS v2.50.6 UI, saved-project, export, and weighted-stipple source wiring')

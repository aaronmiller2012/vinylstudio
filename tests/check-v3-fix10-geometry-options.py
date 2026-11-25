#!/usr/bin/env python3
"""Guard the UI → renderer seam and gallery-to-cutter bounds end to end."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
ui=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
cell=(r/'app/src/main/java/com/halftonestudio/LayeredCellPortraitEngine.java').read_text()
tess=(r/'app/src/main/java/com/halftonestudio/TessellationEngine.java').read_text()
assert 'layerSeam=Math.max(layeredSeamMm,cutGapFloor)' in ui
assert 'cells.seamGapMm=layerSeam;cells.minFeatureMm=layerMin;' in ui
assert 'cells.gapMm=mosaicGap' in ui
assert 'seamFloor=StyleCutGeometryPreflight.exportGapMm(settings.bridgeMm,settings.minimumMm)+.06f' in ui
assert 'Math.max(layeredSeamMm,seamFloor)' in ui
assert 'Math.min(20.1f,Math.max(.5f,seamFloor+.50f))' in ui
assert 'o.seamGapMm>20.1f' in cell and 'o.gapMm>20.1f' in cell
assert '!Float.isFinite(opts.gapMm)' in tess and 'opts.gapMm>20.1f' in tess
motif=(r/'app/src/main/java/com/halftonestudio/TessellationMotifEngine.java').read_text()
poly=(r/'app/src/main/java/com/halftonestudio/LowPolyDelaunayEngine.java').read_text()
assert 'gapMm>20.1f' in motif
assert 'gapMm > 20.1f' in poly
print('PASS cell/mosaic UI values, exported profile bounds, low-poly and finite gap checks')

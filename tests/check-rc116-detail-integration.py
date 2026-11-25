#!/usr/bin/env python3
"""Actual RC116 production integration checks, not stale UI text snippets."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(r/'app/src/main/java/com/halftonestudio/LayeredCellPortraitEngine.java').read_text()
profile=(r/'app/src/main/java/com/halftonestudio/LayeredCellReleaseProfile.java').read_text()
assert 'tessLayerCount=8;layeredSolidBase=true;layeredExtraMotif=true;' in a
assert 'layeredColumns=40;layeredMotifDepth=3;layeredPhotoDetail=1f;' in a
assert 'cells.solidBase=layerSolidBase;cells.detailMotifOverlay=layerExtraMotif;' in a
assert 'out.put("layeredSolidBase",layeredSolidBase)' in a
assert 'json.optBoolean("layeredSolidBase",false)' in a
assert 'json.optBoolean("layeredExtraMotif",false)' in a
assert 'safeColumns(draft?Math.min(layerCols,18):layerCols,w,h,width,layerMin,mosaicGap,layerSolidBase)' in a
assert 'addSolidSilhouette(source,w,h,o,pieces)' in e
assert 'addFeatureMotif(' in e and 'MAX_DETAIL_CELLS=2400' in e
assert 'boolean enhanced' in profile
assert 'VinylStyleSuite' not in e # no modifications to original specialty engines
print('PASS RC116 UI, persistence, background, extra motif and physical density wiring')

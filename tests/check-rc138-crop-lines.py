#!/usr/bin/env python3
"""RC138 source contract: crop-aware carrier tracing without old-engine changes."""
from pathlib import Path
import json
from release_version import at_least
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/LineArtEngine.java').read_text()
helper=(root/'app/src/main/java/com/halftonestudio/LineCropBoundary.java').read_text()
meta=json.loads((root/'tests/release-info.json').read_text())
assert meta['versionCode']>=416 and at_least(meta['versionName'],'2.55.4')
assert s.count('LineCropBoundary.accepts(s.circleCrop,x,y,s.widthMm,r.heightMm,')==2, 'Both classic and modern line tracers must enforce circular boundary'
assert 'if(!circleCrop)return true;' in helper, 'Non-cropped geometry must remain unchanged'
assert 'Math.max(0f,halfWidthMm)+.035f' in helper, 'Cut boundary must protect full ribbon half-width'
assert 'VinylEngine.add(r,li,VinylEngine.Shape.polygon(xy));' in s, 'Existing SVG polygon output must be retained'
assert 'insideCutBoundary(shape,r.settings.widthMm,r.heightMm,r.settings.marginMm,r.settings.circleCrop)' in (root/'app/src/main/java/com/halftonestudio/VinylEngine.java').read_text()
assert 'tests/run-rc137-quality-gate.sh' in (root/'tests/run-rc138-crop-lines-gate.sh').read_text(), 'Prior quality gate cannot be lost'
print('PASS RC138 crop-boundary checks in both classic and modern tracers; no-crop path preserved')

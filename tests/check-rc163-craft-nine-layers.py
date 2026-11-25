#!/usr/bin/env python3
"""RC163 integration assertions; executable geometry coverage is in CraftPatternRC163Test."""
import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
src=r/'app/src/main/java/com/halftonestudio'
meta=json.loads((r/'tests/release-info.json').read_text())
assert(meta['version'],meta['versionCode'],meta['gate'])==('2.55.28',440,'tests/run-rc163-craft-nine-layers-gate.sh')
assert meta['apk']=='VinylHalftoneStudio-v2.55.28-CraftNineLayers-RC163.apk'
activity=(src/'StudioActivity.java').read_text()
for name in ['CAMOUFLAGE','LEAF_VEIN','FISHNET','LACE','CROCHET']:
    assert 'VinylStyleSuite.'+name in activity,('Picker missing',name)
assert 'newStyleColors=expandNewStyleSwatches(physicalColors)' in activity
assert 'VinylStyleSuite.isCraft(id)?9' in activity
assert 'newStyleColors[i]=newColorJson.optInt' in activity
assert 'for(int i=0;i<9;i++)' in activity
assert 'newStyleColorCount=Math.max(NewStyleColorSlots.minimumSelectable(id)' in activity
assert 'NewStyleColorSlots.maximumSelectable(newStyleId)' in activity
assert 'NewStyleLayerPolicy.isStructuralLayer(newStyleId,index)' in activity
assert 'c.colorCount>(isCraft(c.style)?9:8)' in (src/'VinylStyleSuite.java').read_text()
code=(src/'NineLayerCraftPatternEngine.java').read_text()
assert 'surface.fillValid(0)' in code
assert 'assignQuantiles(cells,colors,o.layers)' in code
assert 'if(result.layers.get(l).loops.isEmpty())' in code
assert 'CancellationCheckpoint.check()' in code
assert (src/'StudioThumbnailRegistry.java').read_text().count('multilayer.')>=25
assert (src/'NewStyleColorSlots.java').read_text().count('9')>=3
for sh in ['BUILD_ON_NAS.sh','transfer/PHONE_BUILD_AND_RECEIVE.sh','transfer/NAS_STAGE_BUILD.sh']:
    assert 'RC163' in (r/sh).read_text() or 'rc163' in (r/sh).read_text(),sh
print('PASS RC163 all five style UI entries, nine swatches, L1 policy, project persistence and NAS build wiring')

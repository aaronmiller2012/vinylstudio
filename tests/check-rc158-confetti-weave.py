#!/usr/bin/env python3
"""RC158 release metadata, style migration, new standalone picker modes, lint-safe engine."""
from pathlib import Path
import json,re
r=Path(__file__).resolve().parents[1]
def read(name):return (r/name).read_text()
m=json.loads(read('tests/release-info.json'))
assert m=={'version':'2.55.23','versionCode':435,
 'versionName':'2.55.23-confetti-weave-rc158',
 'apk':'VinylHalftoneStudio-v2.55.23-ConfettiWeave-RC158.apk',
 'gate':'tests/run-rc158-confetti-weave-gate.sh'}
suite=read('app/src/main/java/com/halftonestudio/VinylStyleSuite.java')
ui=read('app/src/main/java/com/halftonestudio/StudioActivity.java')
scatter=read('app/src/main/java/com/halftonestudio/FallenConfettiEngine.java')
weave=read('app/src/main/java/com/halftonestudio/InterlockingChainmailEngine.java')
reg=read('app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java')
assert 'CONFETTI=27, CHAINMAIL=28, SPIDER_WEB=29' in suite
assert 'CONFETTI_ROUND=30' in suite and 'CONFETTI_FALLEN=34' in suite
assert 'case CELTIC_BRAID:case ARCHITECTURAL:case SPIDER_WEB:return true' in suite
assert 'VinylStyleSuite.isConfetti(newStyleId)' in ui
assert 'for(int id:new int[]{VinylStyleSuite.CONFETTI_ROUND' in ui
assert 'addPickerChoice(out,multi,VinylStyleSuite.NAMES[id]' in ui
assert 'FallenConfettiEngine.MODE_FALLEN' in suite
assert 'MODE_MIXED=0' in scatter and 'SQUARE=3' in scatter
assert 'InterlockingChainmailEngine.render(surface,o,colors)' in read('app/src/main/java/com/halftonestudio/PatternPortraitTrioEngine.java')
assert 'surface.fillValid(0)' in weave
assert 'occupied[slot]++' in weave and 'rank' in weave
assert 'TOP_LEVEL_COUNT = 62' in reg
assert re.search(r'\brnd\.nextFloat\(',scatter) is None
assert re.search(r'\brnd\.nextBoolean\(',scatter) is None
assert 'minSdk 26' in read('app/build.gradle')
assert 'lint-baseline.xml' not in read('app/build.gradle')
assert 'colorSwatchGrid(g,layerColors,layerNames,layerPopulated,newStyleSelectedLayer' in ui
assert 'NewStyleLayerPolicy.isStructuralLayer(newStyleId,index)' in ui
for fn in ['PHONE_BUILD_AND_RECEIVE.sh','NAS_STAGE_BUILD.sh','PHONE_RECEIVE_ONLY.sh']:
    assert 'RC158' in read('transfer/'+fn)
print('PASS RC158 metadata, six picker modes, ring assignment, legacy IDs, API-26 safety, layer controls')

#!/usr/bin/env python3
"""RC160 current release metadata and auto-vs-manual foundation invariants."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
read=lambda x:(r/x).read_text()
info=json.loads(read('tests/release-info.json'))
assert info['versionCode']>=437 and info['version'].startswith('2.55.'),info
assert info['apk'] and info['gate'],info
ui=read('app/src/main/java/com/halftonestudio/StudioActivity.java')
foundation=read('app/src/main/java/com/halftonestudio/FoundationColorPolicy.java')
tess=read('app/src/main/java/com/halftonestudio/TessellationMotifEngine.java')
policy=read('app/src/main/java/com/halftonestudio/NewStyleLayerPolicy.java')
assert 'bestIndex(' in foundation and 'anchorPhotoPalette(' in foundation
assert 'autoSilhouetteColorManual=true' in ui
assert 'autoSilhouetteColorManual=json.optBoolean' in ui
assert 'out.put("autoSilhouetteColorManual",autoSilhouetteColorManual)' in ui
assert 'final int[] palettePixels=rgba;' in ui
assert 'preparedPhotoColorPixels=palettePixels;' in ui
assert 'updateAutomaticSnipFoundation(suggested,palettePixels,false)' in ui
assert 'FoundationColorPolicy.anchorPhotoPalette' in ui
assert 'layeredCellMode&&(offsetCellMode||layeredSolidBase)' in ui
assert 'o.solidBase&&o.layerCount>=2' in tess
assert 'style==VinylStyleSuite.TESSELLATION_MOTIF' in policy
assert 'style==VinylStyleSuite.PRISMATIC_DIAGONAL' in policy
assert 'minSdk 26' in read('app/build.gradle')
assert 'lint-baseline.xml' not in read('app/build.gradle')
for filename in ['PHONE_BUILD_AND_RECEIVE.sh','NAS_STAGE_BUILD.sh','PHONE_RECEIVE_ONLY.sh']:
    assert ('RC'+str(info['versionName'].split('-rc')[-1])) in read('transfer/'+filename)
print('PASS RC160 release metadata, foundation scope, manual override persistence and transfer')

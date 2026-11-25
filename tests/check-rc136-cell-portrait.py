#!/usr/bin/env python3
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
ui=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
schema=(root/'app/src/main/java/com/halftonestudio/ProjectSettingsSchema.java').read_text()
meta=json.loads((root/'tests/release-info.json').read_text())
assert meta['versionCode']>=414
from release_version import at_least
assert at_least(meta['versionName'], '2.55.2')
assert 'Fine-detail motif sheet:' not in ui
assert 'layerExtraMotif=(capMode||scaleMode)&&layeredExtraMotif;' in ui
assert 'if(offsetCellMode||(!bottleCapMode&&!fishScaleMode))layeredExtraMotif=false;' in ui
assert 'out.put("layeredExtraMotif",(bottleCapMode||fishScaleMode)&&layeredExtraMotif);' in ui
assert 'tessLayerCount=8;layeredSolidBase=true;layeredExtraMotif=false;' in ui
assert 'upper colors refine photographic detail.' in ui
assert 'static final int CURRENT=29;' in schema
assert 'if(!bottle&&!fish)out.put("layeredExtraMotif",false);' in schema
print('PASS RC136 removes Layered Cell fine-detail motif sheet UI, defaults, save/load, and migration')

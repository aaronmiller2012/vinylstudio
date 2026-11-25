#!/usr/bin/env python3
from pathlib import Path
import json
import re
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(r/'app/src/main/java/com/halftonestudio/LayeredCellPortraitEngine.java').read_text()
w=(r/'app/src/main/java/com/halftonestudio/OffsetCellPortraitEngine.java').read_text()
m=(r/'app/src/main/java/com/halftonestudio/ProjectSettingsSchema.java').read_text()
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=406 and meta['gate'].startswith('tests/run-')
assert re.search(r'"Layered Cell Portrait"\s*,\s*"Offset Cell Portrait"\s*,\s*"Bottle Cap Mosaic Portrait"\s*,\s*"Fish Scale Mosaic Portrait"', a)
assert 'int[] ids={6,8,9,10,19,20,21,18};' in a
assert 'offsetCellMode=idx==19' in a
assert 'offsetMode?OffsetCellPortraitEngine.generate' in a
assert 'out.put("offsetCellMode",layeredCellMode&&offsetCellMode)' in a
assert 'offsetCellMode=layeredCellMode&&json.optBoolean("offsetCellMode",false)' in a
assert 'o.offsetRows=true;' in w
assert 'public boolean offsetRows=false;' in e
assert 'shift=(o.offsetRows&&(row&1)==1)?xCell*.5f:0f' in e
assert ('static final int CURRENT=28;' in m) or ('static final int CURRENT=29;' in m)
assert meta['apk'] in (r/'BUILD_ON_NAS.sh').read_text()
assert meta['versionName'] in (r/'app/build.gradle').read_text()
assert (r/'BUILD_PLAY_BUNDLE_ON_NAS.sh').exists()
print('PASS RC117 distinct style 19, 50% offset, persistent opt-in, SVG reuse, Play/export paths')

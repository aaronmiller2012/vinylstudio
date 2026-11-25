#!/usr/bin/env python3
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(r/'app/src/main/java/com/halftonestudio/LayeredCellPortraitEngine.java').read_text()
m=(r/'app/src/main/java/com/halftonestudio/ProjectSettingsSchema.java').read_text()
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=406 and meta['gate'].startswith('tests/run-')
for text in ('"Bottle Cap Mosaic Portrait"','BottleCapMosaicEngine.generate(colors,w,h,cells)','bottleCapMode=idx==20','out.put("bottleCapMode",layeredCellMode&&bottleCapMode)','bottleCapMode=layeredCellMode&&json.optBoolean("bottleCapMode",false)'):
    assert text in a,text
assert 'public boolean bottleCaps=false;' in e
assert ('static final int CURRENT=28;' in m) or ('static final int CURRENT=29;' in m)
assert 'version<23' in m
assert meta['apk'] in (r/'BUILD_ON_NAS.sh').read_text()
assert meta['versionName'] in (r/'app/build.gradle').read_text()
print('PASS RC118 distinct Bottle Cap style, saved mode, cut geometry and metadata')

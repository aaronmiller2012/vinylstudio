#!/usr/bin/env python3
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(r/'app/src/main/java/com/halftonestudio/LayeredCellPortraitEngine.java').read_text()
schema=(r/'app/src/main/java/com/halftonestudio/ProjectSettingsSchema.java').read_text()
meta=json.loads((r/'tests/release-info.json').read_text())
for text in ('"Fish Scale Mosaic Portrait"','FishScaleMosaicEngine.generate(colors,w,h,cells)',
             'fishScaleMode=idx==21','out.put("fishScaleMode",layeredCellMode&&fishScaleMode)',
             'fishScaleMode=layeredCellMode&&json.optBoolean("fishScaleMode",false)'):
    assert text in a,text
assert 'public boolean fishScales=false;' in e
assert 'if(o.fishScales)return generateFishScales(source,w,h,o);' in e
assert ('static final int CURRENT=28;' in schema) or ('static final int CURRENT=29;' in schema)
assert 'version<24' in schema
assert meta['versionCode']>=406 and meta['gate'].startswith('tests/run-')
assert meta['apk'] in (r/'BUILD_ON_NAS.sh').read_text()
assert meta['versionName'] in (r/'app/build.gradle').read_text()
assert 'tests/run-rc125-palette-offset-gate.sh' in (r/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()
print('PASS RC119 Fish Scale UI, engine, persistence, compatibility and release metadata')

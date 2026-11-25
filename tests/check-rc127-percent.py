#!/usr/bin/env python3
import re,json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
a=(r/'app/src/main/java/com/halftonestudio/AutoPhotoTuner.java').read_text()
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=406 and meta['gate'].startswith('tests/run-')
assert f"versionCode {meta['versionCode']}" in (r/'app/build.gradle').read_text()
for name,low in [('Detail · point density %',50),('Dot size · %',60),('2 · Dot coverage · %',35)]:
    hits=[x for x in s.splitlines() if f'"{name}"' in x and 'slider(' in x]
    assert len(hits)==1, (name,hits)
    assert f',{low},100,5,' in hits[0],hits
assert 'Math.min(2,json.optDouble("stipplePointDensity"' not in s
assert 'Math.min(1.6,json.optDouble("stippleDotSize"' not in s
assert 'up to 2×' not in s
assert 'sliderValueText(label,initialValue,increment)' in s and 'sliderValueText(label,v,increment)' in s, 'Slider labels must use exact step precision'
assert 'PercentControlLimits.pointDensity(start.stippleDensity)' in s
assert 'PercentControlLimits.pointDensity(r.stippleDensity)' in s
assert 'PercentControlLimits.pointDensity' in a
schema=(r/'app/src/main/java/com/halftonestudio/ProjectSettingsSchema.java').read_text()
assert ('CURRENT=28;' in schema) or ('CURRENT=29;' in schema)
print('PASS RC127 percentage slider limits and auto-tune/load wiring')

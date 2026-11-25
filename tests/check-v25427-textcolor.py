#!/usr/bin/env python3
"""RC61 wiring tests; algorithm behavior tested with real SVG geometry in Java."""
import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
d=r/'app/src/main/java/com/halftonestudio'
s=(d/'TextHalftoneEngine.java').read_text()
c=(d/'TextColorSeparation.java').read_text()
m=json.loads((r/'tests/release-info.json').read_text())
assert m['versionCode']>=339 and m['versionName'] in (r/'app/build.gradle').read_text()
assert m['versionName'] in (r/'app/build.gradle').read_text()
assert m['gate'] in (r/'BUILD_ON_NAS.sh').read_text()
assert 'TextPortraitRegions.analyze' in s and 'StyleKit.labDist2' in c
assert 'WordPortraitEngine.render' in s and 'whole words' in (d/'WordPortraitEngine.java').read_text()
assert 'VinylStyleSuite.subjectLoops(src)' in s and 'snipped-background-1' in s
assert 'o.silhouetteUnderlay=true' in (d/'VinylStyleSuite.java').read_text()
assert 'android.useAndroidX=true' in (r/'gradle.properties').read_text()
print('PASS RC61 version, AndroidX, palette evaluator, whole words, filled Snip base')

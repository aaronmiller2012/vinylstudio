#!/usr/bin/env python3
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
d=root/'app/src/main/java/com/halftonestudio'
u=(d/'StudioActivity.java').read_text(); e=(d/'VinylEngine.java').read_text();w=(d/'WordScreenMotif.java').read_text()
info=json.loads((root/'tests/release-info.json').read_text())
assert info['versionCode']==343 and info['gate']=='tests/run-v25431-text-shapes-gate.sh'
assert info['versionName'] in (root/'app/build.gradle').read_text()
assert info['gate'] in (root/'BUILD_ON_NAS.sh').read_text()
assert 'ASCII=27,WORD_SCREEN=28' in e and 'DASH,ASCII,WORD_SCREEN' in e
assert 'WordScreenMotif.generate(result,pixels,w,h,i)' in e
assert 'screenWords=screenWords;' in e
assert 'out.put("screenWords",s.screenWords)' in u and 'json.optString("screenWords"' in u
assert 'VinylEngine.SHAPE_STYLES' in u and 'showShapePickerDialog()' in u
assert 'showTextPortraitPickerDialog()' not in u
assert 'VinylEngine.WORD_SCREEN' in u and 'VinylEngine.ASCII' in u
assert 'case VinylStyleSuite.TEXT' in u and 'activateNewStyle(VinylStyleSuite.TEXT)' in u
assert 'VinylEngine.coverage(' in w and 'VinylEngine.add(' in w and 'wordShape(' in w
assert 'android.useAndroidX=true' in (root/'gradle.properties').read_text()
assert not list(d.glob('*DotToDot*'))
print('PASS RC65 release metadata, independent tiles, editable words, legacy compatibility')

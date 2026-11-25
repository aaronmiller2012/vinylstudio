#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/com/halftonestudio'
a=(p/'StudioActivity.java').read_text()
g=(p/'VinylQuickStartGuide.java').read_text()
c=(p/'VinylGuidePolicy.java').read_text()
s=(p/'VinylTutorialSample.java').read_text()
x=ET.parse(r/'app/src/main/res/values/strings.xml').getroot()
strings={e.attrib.get('name') for e in x.findall('string')}
for i in range(5):
    for name in (f'guide_title_{i}',f'guide_body_{i}',f'guide_tip_{i}'):
        assert name in strings,name
assert 'showFirstTimeGuide=VinylQuickStartGuide.isFirstInstall' in a
assert 'postUi(()->showQuickStartGuide(false,false))' in a
assert 'showFirstTimeGuide){' in a
assert 'VinylQuickStartGuide.show(this' in a
assert 'VinylTutorialSample.create()' in a
assert 'PhotoCropEditor.CropResult(sample' in a
assert 'quickStartGuideSeenV2494' in g
assert 'firstInstallTime' in g and 'lastUpdateTime' in g
assert 'putBoolean(KEY_SEEN,true)' in g
assert 'putBoolean(KEY_TIPS,!disable.isChecked())' in g
assert 'setOnDismissListener' in g
assert 'MotionEvent.ACTION_UP' in g
assert 'R.string.guide_menu' in a
assert 'getPreferences(MODE_PRIVATE)' in a
assert 'tipsEnabled' in g
assert 'VinylGuidePolicy.mayShowTip' in g
assert 'new Bitmap' not in s
import json
code=json.loads((r/'tests/release-info.json').read_text())['versionCode']
assert f'versionCode {code};' in (r/'app/build.gradle').read_text()
print('PASS v2.49.4: five-page first-run guide, old-install guard, replay, tips, offline sample')

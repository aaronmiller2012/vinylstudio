#!/usr/bin/env python3
"""RC75 inherited advanced-panel and dialog checks, updated for RC104 black/white picker."""
from pathlib import Path
import json,re
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
w=(r/'app/src/main/java/com/halftonestudio/VinylColorWheel.java').read_text()
e=(r/'app/src/main/java/com/halftonestudio/VinylColorEditor.java').read_text()
info=json.loads((r/'tests/release-info.json').read_text())
g=(r/'app/build.gradle').read_text();b=(r/'BUILD_ON_NAS.sh').read_text()
assert info['versionCode']>=353 and info['gate'] in b and info['apk'] in b
assert info['versionName'] in g and re.search(r'\bversionCode\s+'+str(info['versionCode'])+r'\b',g)
assert info['apk'] in b and info['gate'] in b
assert 'Advanced stipple settings' not in s
assert 'Advanced line refinement' not in s
assert 'advancedStipple' not in s and 'advancedLineRefinement' not in s
assert 'advancedOpen' not in s and 'advancedHalftone' not in s
assert 'if(stippleSettings.adaptiveCoverage)' in s
assert 'action(host,stippleSettings.adaptiveCoverage?' in s
assert 'if(SmartHalftonePresets.ribbons(settings.pattern))' in s
assert 'if(settings.mode==VinylEngine.CMYK && hasMidtoneRibbonLayer())' in s
assert s.count('new VinylColorEditor(this,')>=5
assert 'new VinylColorWheel(this)' not in s
assert s.count('setView(scrollColorEditor(editor))')>=4
assert 'setView(scrollColorEditor(body))' in s
assert 'SeekBar' in e and 'value.setMax(100)' in e
assert ('Value / Brightness' in e or 'Value / Brightness' in (r/'app/src/main/res/values/strings.xml').read_text()) and 'Hue / Saturation' in e and 'Solid Black' in e and 'Solid White' in e
assert 'new EditText' not in e and 'wheel.setBrightness(progress/100f)' in e
assert 'Color.colorToHSV(color,hsv)' in e
assert 'if(hsv[2]<.05f)hsv[2]=1f;' not in w
assert 'getColorOrThrow()' in e and 'current.color=initialColor;' in s
print('PASS RC104 always-visible HSV/Value/Black/White, HSV editors, inline style controls, release identity')

#!/usr/bin/env python3
"""RC104 verifies app project default, top hue control, and exact HSV shortcuts."""
from pathlib import Path
import re,json
p=Path(__file__).resolve().parents[1]
a=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(p/'app/src/main/java/com/halftonestudio/VinylColorEditor.java').read_text()
v=(p/'app/src/main/java/com/halftonestudio/VinylEngine.java').read_text()
s=(p/'app/src/main/java/com/halftonestudio/VinylSizing.java').read_text()
meta=json.loads((p/'tests/release-info.json').read_text())
g=(p/'app/build.gradle').read_text();b=(p/'BUILD_ON_NAS.sh').read_text()
assert meta['versionCode']>=382
assert meta['versionName'] in g and re.search(r'\bversionCode '+str(meta['versionCode'])+r'\b',g)
assert meta['apk'] in b and meta['gate'] in b
assert 'MINIMUM_CUT_WIDTH_MM = 0.254f' in v
assert 'fresh.minimumMm=VinylEngine.MINIMUM_CUT_WIDTH_MM' in a
assert 'private VinylEngine.Settings settings=newProjectSettings();' in a
assert a.count('settings=newProjectSettings();')>=2
assert 'json.optDouble("minimumMm",VinylEngine.MINIMUM_CUT_WIDTH_MM)' in a
assert 's.minimumMm=VinylEngine.toMm(profile==' not in s
assert 'inchSlider(a,"Minimum cut width",settings.minimumMm,.010f' in a
for method in ('newStyleColorsPanel','colorPaletteOnlyPanel'):
    part=a.split('private void '+method+'(){',1)[1].split('private void ',1)[0]
    assert part.count('vinylPaletteHueControl(group());')==1,(method,'hue duplicate')
    assert part.index('vinylPaletteHueControl(group());')<part.index('section(paper,"Mounting surface")' if method=='newStyleColorsPanel' else 'section(b,"Mounting surface")'),method
assert a.count('vinylPaletteHueControl(group());')==2,'Hue control must not appear below palettes'
assert 'addSolidColorButton(shortcuts,"Solid Black",Color.BLACK);' in e
assert 'addSolidColorButton(shortcuts,"Solid White",Color.WHITE);' in e
assert 'new EditText' not in e and 'HEX color' not in e
assert 'wheel.setColor(rgb);' in e and 'refresh(rgb);' in e
assert 'public int getColorOrThrow(){return getColor();}' in e
assert 'wheel.setBrightness(progress/100f)' in e
assert 'if(listener!=null)listener.onColor(color);' in e
assert 'Color.colorToHSV(color,hsv)' in e
print('PASS RC104: 0.010-inch project default, automatic profile retention, top Hue, black/white picker, release metadata')

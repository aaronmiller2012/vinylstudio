#!/usr/bin/env python3
from pathlib import Path
import json
root=Path('app/src/main/java/com/halftonestudio')
ui=(root/'StudioActivity.java').read_text()
suite=(root/'VinylStyleSuite.java').read_text()
engine=(root/'TextHalftoneEngine.java').read_text()
start=ui.index('private void showUnifiedStyleDrawer()')
end=ui.index('private void activateNewStyle(int id)',start)
drawer=ui[start:end]
assert 'styleDrawerRow(list,"CMYK"' not in drawer, 'CMYK must not appear as a geometry category'
assert drawer.count('styleDrawerRow(')==5
assert '"Shapes"' in drawer and '"Lines"' in drawer and '"Multilayer"' in drawer
assert 'showShapePickerDialog()' in drawer and 'showLinesPickerDialog()' in drawer
assert 'void setCmyk()' in ui and 'cmykPaletteTile(' in ui, 'retain CMYK mode via Colors'
assert 'VinylEngine.ASCII' in ui and 'VinylEngine.WORD_SCREEN' in ui and 'case VinylStyleSuite.TEXT' in ui
assert 'glyphForPatch(' in engine and 'shouldPlaceWord(' in engine
assert 'c-o.minStrokeMm' in engine and 'if(!fits){skipped++;continue;}' in engine
assert 'o.edgeAware=true;o.orderedDither=true' in suite
assert 'Dot-to-Dot Portrait' not in ui
meta=json.loads(Path('tests/release-info.json').read_text())
assert meta['versionCode']>=337 and meta['versionName'] in Path('app/build.gradle').read_text()
assert meta['versionName'] in Path('app/build.gradle').read_text()
assert meta['gate'] in Path('BUILD_ON_NAS.sh').read_text()
print('PASS RC59 picker, CMYK palette, text detail wiring and release metadata')

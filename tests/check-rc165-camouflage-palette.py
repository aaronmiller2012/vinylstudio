#!/usr/bin/env python3
"""RC165 palette authority and release wiring: supplements executable JVM geometry tests."""
from pathlib import Path
import json
p=Path(__file__).resolve().parents[1]
meta=json.loads((p/'tests/release-info.json').read_text())
assert meta['versionCode']==442 and meta['version']=='2.55.30'
assert meta['gate']=='tests/run-rc165-camouflage-palette-gate.sh'
assert meta['apk']=='VinylHalftoneStudio-v2.55.30-CamoPaletteDefaults-RC165.apk'
ui=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
for line in ['CamouflagePalette.useFamilyDefault(id,savedPalette,',
             'newStyleMatchVinyl=useCamoDefaults;',
             'config.palette=NewStyleColorSlots.fixedPalette(',
             'newStylePhotoAutoMemory[newStyleId]=!newStyleMatchVinyl;',
             'entry.put("explicitPhotoAuto",newStylePhotoAutoMemory[i]);',
             'newStylePhotoAutoMemory[i]=item.optBoolean("explicitPhotoAuto",false);',
             'Restore "+CamouflagePalette.name(camoStyle)+" camouflage colors',
             'newStylePalettePickerTile(row,CamouflagePalette.name(style)+" camouflage"',
             'physicalColors.length==9']:
    assert line in ui,line
assert 'newStyleMatchVinyl=useCamoDefaults||newStyleManualMemory[id];' in ui
assert "versionCode 442" in (p/'app/build.gradle').read_text()
assert meta['gate'] in (p/'BUILD_ON_NAS.sh').read_text()
assert meta['gate'] in (p/'.github/workflows/verify.yml').read_text()
print('PASS RC165 camo palette wiring, active color mode, restoration, explicit photo mode, releases')

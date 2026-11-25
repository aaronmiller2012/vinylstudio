#!/usr/bin/env python3
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
needed=["newStyleId", "showMultilayerPickerDialog()", "showSingleLinePickerDialog()", "startNewStyleRender(draft)",
        "newStyleColorsPanel()", "newStyleLayersPanel()", "drawNewStyleVectors(cv,newStyleVectors)",
        "exportNewStyle(uri)", 'out.put("newStyleId",newStyleId)',
        'newStyleId=VinylStyleSuite.restoredStyleId(storedStyleId)', "NewStyleExport.layerSvg"]
for n in needed:assert n in a,n
for name in ['VinylStyleSuite.java','NewStyleVectorCache.java','NewStyleExport.java','StyleKit.java',
             'LowPolyDelaunayEngine.java','StainedGlassEngine.java','TspArtEngine.java',
             'LinocutEngine.java','TruchetTilesEngine.java','TextHalftoneEngine.java',
             'TopographicLayersEngine.java','RidgeLinesEngine.java']:
 assert (r/'app/src/main/java/com/halftonestudio'/name).is_file(),name
meta=json.loads((r/'tests/release-info.json').read_text())
assert 'versionCode '+str(meta['versionCode']) in (r/'app/build.gradle').read_text()
assert meta['versionName'] in (r/'app/build.gradle').read_text()
print('PASS eight-style UI, source dependencies, save/restore and release metadata')

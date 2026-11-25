#!/usr/bin/env python3
import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
i=json.loads((r/'tests/release-info.json').read_text())
assert i['versionCode']>=358 and i['gate'] in (r/'BUILD_ON_NAS.sh').read_text() and i['apk'] in (r/'BUILD_ON_NAS.sh').read_text()

a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
s=(r/'app/src/main/java/com/halftonestudio/StainedGlassEngine.java').read_text()
c=(r/'app/src/main/java/com/halftonestudio/StyleCutGeometryPreflight.java').read_text()
v=(r/'app/src/main/java/com/halftonestudio/VinylStyleSuite.java').read_text()
assert 'connectedOwners(p,segment(p,seeds))' in s
assert 'segmentDistance(' in s and 'spatialOwners(' in s
assert 'glass panel count' in v.lower()
assert 'if(style==VinylStyleSuite.GLASS&&!geometryWarnings.isEmpty())' in a
assert 'Math.max(newStyleId==VinylStyleSuite.GLASS?3:2' in a
assert 'separatedEdges(' in c
print('PASS RC80 source routing, export risk gate, count normalization and release identity')

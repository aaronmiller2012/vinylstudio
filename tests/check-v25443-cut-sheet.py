#!/usr/bin/env python3
"""Explicit physical cut sheet selection, black layer isolation and export parity."""
from pathlib import Path
import json,re
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
v=(r/'app/src/main/java/com/halftonestudio/VinylEngine.java').read_text()
i=json.loads((r/'tests/release-info.json').read_text())
g=(r/'app/build.gradle').read_text();b=(r/'BUILD_ON_NAS.sh').read_text()
assert i['versionCode'] in (355,356,357,358,359,360,361,362,363,364) and i['gate'] in b and i['apk'] in b
assert i['versionName'] in g and ('versionCode '+str(i['versionCode'])) in g and i['gate'] in b and i['apk'] in b
assert 'public boolean excludeFromExport=false' in v
assert 'l.excludeFromExport=excludeFromExport' in v
cut=v[v.index('public static boolean cutLayer('):v.index('public static int exportedShapeCount(')]
assert 'l.enabled && !l.excludeFromExport && !result.shapes.get(index).isEmpty()' in cut
assert 's.paper' not in cut and 's.substrateAware' not in cut
assert '"excludeFromExport",l.excludeFromExport' in a and '"excludeFromExport",old.excludeFromExport' in a
assert 'l.excludeFromExport=o.optBoolean("excludeFromExport",false)' in a
assert 'layer.excludeFromExport=item.optBoolean("excludeFromExport",false)' in a
assert 'Restore this cut sheet' in a and 'Skip this cut sheet' in a
assert '0 exported cuts' in a and 'design contours retained' in a
assert 'int inspectionPaper=r.settings.paper;' in a and 'if(chosen<0)drawUnderlayInArtworkMm(cv,false)' in a
assert 'latest.settings.layers.get(index).excludeFromExport=cutSheet.excludeFromExport' in a
assert 'if(latest!=null)latest.settings.paper=color' in a
assert 'if(!VinylEngine.cutLayer(result,i))continue' in a
assert 'int nextSheet=base==null?1:2;' in a and 'int sheetNo=base==null?1:2;' in a
assert 'No included vinyl cut sheets.' in a
assert 'vinyl-visibility' in a
assert 'paletteReversed' in a
print('PASS RC77 explicit sheet export, neutral isolation, true cut labels, persistence, release identity')

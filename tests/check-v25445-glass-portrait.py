#!/usr/bin/env python3
from pathlib import Path
import json,re
r=Path(__file__).resolve().parents[1]
i=json.loads((r/'tests/release-info.json').read_text())
g=(r/'app/build.gradle').read_text();b=(r/'BUILD_ON_NAS.sh').read_text()
assert i['versionCode']>=357
assert ('versionCode '+str(i['versionCode'])) in g and i['versionName'] in g and i['apk'] in b and i['gate'] in b
s=(r/'app/src/main/java/com/halftonestudio/StainedGlassEngine.java').read_text()
assert 'segment(Pixels' in s and 'refineSites(Pixels' in s and 'insetMask(Pixels' in s
assert 'TopographicLayersEngine.contours' in s and 'StyleKit.kmeans' in s
assert 'Math.min(o.maxPieces' in s and 'StyleKit.checkCancelled' in s
assert 'o.palette.clone()' in s and 'if(!p.valid[i])continue' in s
for n in ('Options','generate','LeadMode','CUTOUT','UNDERLAY','NONE'):assert n in s
print('PASS RC79 engine routing, bounded LAB segmentation, per-pane palette and metadata')

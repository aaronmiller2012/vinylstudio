#!/usr/bin/env python3
"""Regression: real one-sheet Snip mode, wheel and release provenance."""
import json,re
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
b=(r/'app/src/main/java/com/halftonestudio/AutoSilhouetteUnderlay.java').read_text()
i=json.loads((r/'tests/release-info.json').read_text())
g=(r/'app/build.gradle').read_text(); nas=(r/'BUILD_ON_NAS.sh').read_text()
assert i['versionCode'] in (351,352,353,354,355,356,357,358,359,360,361,362,363,364) and i['gate'] in nas
assert i['versionName'] in g and re.search(r'versionCode\s+'+str(i['versionCode'])+r'\b',g)
assert i['gate'] in nas and i['apk'] in nas
assert 'DEFAULT_COLOR=0xffffffff' in b
assert 'VinylColorEditor editor=new VinylColorEditor(this,initial)' in s
assert 'Solid Snip background · ' in s and 'LinearLayout card=group(host);' in s and 'host.addView(card);' not in s, 'L1 card must have only one parent'
assert '1 filled shape · ' in s and 'base.cutContours()' in s
assert 'for(int physical=base?1:2;physical<=(base?9:8);physical++)' in s
assert 'if(n==1){selectSilhouetteOnly(true);return;}' in s
assert 'private boolean silhouetteOnlyMode=false' in s and '"silhouetteOnlyMode"' in s
assert 'private void exportSilhouetteOnly(Uri uri,AutoSilhouetteUnderlay.Output base)' in s
assert 'if(silhouetteOnly()&&base!=null){exportSilhouetteOnly(uri,base);return;}' in s
assert 'if(silhouetteOnly()&&currentUnderlay()!=null){exportSilhouetteOnly(uri,currentUnderlay());return;}' in s
assert 'CutSvgCompatibility.verify(svg)' in s
assert 'if(!silhouetteOnly())for(int li=0;li<result.shapes.size();li++)' in s
assert 'if(!silhouetteOnly()&&!basePreviewActive())for(int i=0;i<cache.fills.length;i++)' in s
assert 'if(!silhouetteOnly()&&!basePreviewActive())for(int order=0;order<cache.layers.length;order++)' in s
assert 'out.put("schema",18)' in s
print('PASS RC73 white L1, wheel, 1-sheet selection, visual masking, export-only base, saved project state')

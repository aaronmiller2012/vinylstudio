#!/usr/bin/env python3
"""Guard real L1 card, physical number mapping, separate preview/export states."""
import json,re
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
i=json.loads((r/'tests/release-info.json').read_text())
g=(r/'app/build.gradle').read_text()
b=(r/'BUILD_ON_NAS.sh').read_text()
assert i['versionCode'] in (352,353,354,355,356,357,358,359,360,361,362,363,364) and i['gate'] in b
assert re.search(r'\bversionCode\s+'+str(i['versionCode'])+r'\b',g) and i['versionName'] in g
assert i['apk'] in b and i['gate'] in b
assert 'private boolean previewBaseOnly=false;' in s
assert 'private boolean basePreviewActive(){return previewBaseOnly&&underlayRequested();}' in s
assert 'silhouetteOnlyMode=only;previewBaseOnly=false' in s
assert 'if(!silhouetteOnly()&&!basePreviewActive()&&cache!=null&&cache.source==r)' in s
assert 'if(previewMode==2&&!basePreviewActive())return;' in s
assert 'previewBaseOnly=!previewBaseOnly;chosen=-1' in s
assert 'previewBaseOnly=false;' in s[s.index('private void selectUnifiedLayer('):s.index('private void chooseLayeredLayerFamily(')]
assert 'if(selected>=0||basePreviewActive())action(overview,"Show complete artwork"' in s
segment=s[s.index('private void showAutomaticSilhouette('):s.index('private int[] standardLayerCutCounts')]
assert 'card=group(host)' in segment and 'host.addView(card)' not in segment, 'L1 must be attached to its host exactly once'
assert '1 filled shape · ' in segment and 'base.cutContours()' in segment
assert 'action(row,"Edit",this::changeAutoSilhouetteColor)' in segment
assert '"Use L1 only"' not in segment and 'action(options,"Color wheel"' not in segment
assert 'labels[i]="L"+PhysicalLayerLayout.physicalNumber(i,underlayRequested());' in s
assert 'if(!underlayRequested())layerCountPicker(overview,count' in s
assert 'layerCountPicker(g,newStyleColorCount' in s
assert 'newStyleSelectedLayer=newStyleSelectedLayer==index?-1:index;' in s
assert 'if(silhouetteOnly()&&base!=null){exportSilhouetteOnly(uri,base);return;}' in s
assert 'if(silhouetteOnly()&&currentUnderlay()!=null){exportSilhouetteOnly(uri,currentUnderlay());return;}' in s
assert 'out.put("silhouetteOnlyMode",silhouetteOnlyMode)' in s
print('PASS RC74 first-class L1 card, physical labels, preview/export separation, saved compatibility')

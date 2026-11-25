#!/usr/bin/env python3
"""Integration contracts to prevent a silent slider regression in Android UI code."""
from pathlib import Path
import re
base=Path(__file__).resolve().parents[1]/'app/src/main/java/com/halftonestudio'
s=(base/'StudioActivity.java').read_text()
p=(base/'OpaqueOverlaySwatchPlanner.java').read_text()
mask=(base/'LayeredCellPortraitEngine.java').read_text()
assert 'MasterLayerLinkPolicy.applyIfLinked(settings)' in s
assert 'if(!settings.linkedControls)return;' in (base/'MasterLayerLinkPolicy.java').read_text()
assert 'linkedMasterControlsNotice(controls);' in s
assert 'linkedMasterControlsNotice(a);' in s
assert '"Relink master screen controls"' in s
assert 'settings.linkedControls=false;settings.anglePreset=' not in s
assert s.count('tessellationGapSlider(host,')==4
assert 'normalizeTessellationGap();' in s
assert 'StyleCutGeometryPreflight.exportGapMm(settings.bridgeMm,settings.minimumMm)+.06f' in s
assert 'SliderRangePolicy.format(value,increment,label.contains("%"))' in s
assert 'bar.setProgress(initialProgress);' in s
assert 'boolean ab=polygonsContact(marks[0],marks[1])' in p
assert 'if(ab&&a!=0&&b==a)continue;' in p
assert 'if(c!=0&&(c==a||c==b))continue;' not in p
assert 'gradient(source,o.allowedPixels,w,h,x0,y0,x1,y1)' in mask
assert re.search(r'private static Gradient gradient\(int\[\] pix,boolean\[\] allowed,',mask)
print('PASS linked UI, effective slider range, mask-aware gradient and geometry ownership integration')

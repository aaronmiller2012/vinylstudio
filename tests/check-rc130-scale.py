#!/usr/bin/env python3
"""RC131 fish-scale reference guard: shared boundaries, not isolated ovals."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
engine=(root/'app/src/main/java/com/halftonestudio/LayeredCellPortraitEngine.java').read_text()
ui=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
thumb=(root/'app/src/main/java/com/halftonestudio/StudioStylePreview.java').read_text()
for token in ('fishScaleShape(left,right,row,rowPitch,pitch,',
              'fishEnvelope(x,row*rowPitch,prevShift,pitch,amplitude,half)',
              'fishEnvelope(x,(row+1)*rowPitch,shift,pitch,amplitude,-half)',
              'fishClipCanvas(polygon,w,h)',
              'addFishScalePart(src,w,h,o,mmPx,pieces,selected,cell,scale)',
              'Math.pow(Math.max(0d,Math.sin(Math.PI*u)),1.16d)'):
    assert token in engine, 'Missing tessellating fish-scale geometry: '+token
assert 'layeredColumns=34;layeredExtraMotif=true;' in ui, 'New fish scale selections must restore the optional raised accent sheet'
assert 'case 6: // shared-boundary fish scales' in thumb, 'Fish scale thumbnail missing'
assert 'path.cubicTo(left+2,top+9,left+4,top+15,left+10,top+18.6f)' in thumb, 'Fish scale preview is no longer pointed/scalloped'
print('PASS RC131 shared-edge fish scale geometry, exact canvas clipping and picker thumbnail')

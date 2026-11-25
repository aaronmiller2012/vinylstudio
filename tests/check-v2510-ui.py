#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
panel=a[a.index('if(stippleActive()){',a.index('private void colorPaletteOnlyPanel()')):a.index('if(layeredCellMode){',a.index('private void colorPaletteOnlyPanel()'))]
assert 'for(int first=0;first<8;first+=2)' in panel
assert 'final int layer=i,swatch=tessColors[i]' in panel
assert 'final boolean active=i<tessLayerCount' in panel
assert 'tile.setOnClickListener' in panel and 'else editTessColor(layer)' in panel
assert 'tessLayerCount=layer+1' in panel and 'stippleOrderNormalize()' in panel
assert 'Use all 8 colors' in panel and 'Regenerate swatches from photo' in panel
assert 'stippleShowCutOutlines' in panel and 'Show cut outlines on artwork' in panel
assert 'StipplePhotoPalette.derive(rgba,w,h,8,paper)' in a
assert 'if(idx==18){' in a and 'tessLayerCount=8;' in a
assert 'private static final class StippleVectorCache' in a
assert 'layers[i]=new Path()' in a and 'addCutRing(dest,tile.xy)' in a
assert 'for(float[] hole:tile.holes)addCutRing(dest,hole)' in a
assert 'new StippleVectorCache(mosaicReady)' in a
assert 'stippleVectorCache=vectors;' in a
start=a.index('private void drawStippleVectors(')
end=a.index('@Override protected void onDraw(Canvas cv)',start)
viewer=a[start:end]
assert 'cv.drawPath(cache.layers[layer],paint)' in viewer
assert 'result.stipple.options.order[order]' in viewer
assert 'cv.scale(zoom,zoom)' in viewer
assert 'cv.drawBitmap(' not in viewer and 'Bitmap.createBitmap(' not in viewer
assert 'drawStippleVectors(cv,cuts);return;' in a
assert 'if(result==null&&activeWorkspace!=0&&!alternateActive())' in a
assert 'scaler.onTouchEvent(event)' in a and 'getParent().requestDisallowInterceptTouchEvent(true)' in a
assert 'ViewportMath.zoomPan(' in a
assert 'requestSelectedPreview(){' in a and 'artwork.invalidate();return; // Real vector Paths' in a
from release_helpers import version_code_matches, version_name_matches, apk_name_matches
assert version_code_matches((r/"app/build.gradle").read_text())
assert version_name_matches((r/"app/build.gradle").read_text())
assert apk_name_matches((r/"BUILD_ON_NAS.sh").read_text())
print('PASS: all eight available/editable, independent vector canvas and specialty touch navigation')

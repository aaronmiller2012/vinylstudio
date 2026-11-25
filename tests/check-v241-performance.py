from pathlib import Path
p=Path(__file__).resolve().parents[1]
a=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
v=(p/'app/src/main/java/com/halftonestudio/VinylEngine.java').read_text()
l=(p/'app/src/main/java/com/halftonestudio/LineArtEngine.java').read_text()
canvas=a[a.index('private final class CanvasView extends View'):]
on_draw=canvas[canvas.index('protected void onDraw(Canvas cv)'):canvas.index('/** Android dispatches S Pen hover')]
checks={
  'hardware canvas not forced to software': 'setLayerType(View.LAYER_TYPE_SOFTWARE' not in canvas,
  'min SDK hardware dash fallback': 'Build.VERSION.SDK_INT>=28?marginDashes:null' in on_draw,
  'paths built by worker': 'final CanvasGeometry vectorCache=CanvasGeometry.create(out)' in a,
  'one path per layer': 'new Path[result.shapes.size()]' in a,
  'cached paths drawn': 'cv.drawPath(cache.paths[li],paint)' in on_draw,
  'no paths allocated for color shapes per draw': 'for(VinylEngine.Shape sh:r.shapes.get(li))' not in on_draw,
  'pre-computed mark validation': 'String issue=currentMarkIssue;' in on_draw and 'validateMarks(r)' not in on_draw,
  'bounded pan preview on gesture': 'transforming&&cache.gesturePreview!=null' in on_draw and 'Math.min(1f,800f/Math.max(w,h))' in a,
  'final vector on gesture completion': 'transforming=false;invalidate();' in a,
  'slider final render': 'sliderDragging=false;invalidateToolPanels();requestRender();' in a,
  'draft bounded': 'vectorW=Math.max(1,Math.round(w*ratio))' in a and '760f/maxSide' in a,
  'cancel old render': 'renderController.cancel(StudioRenderController.Lane.STANDARD)' in a and 'cancelLocked(i)' in (p/'app/src/main/java/com/halftonestudio/StudioRenderController.java').read_text(),
  'standard cancellation checkpoints': 'checkGenerationCancelled()' in v,
  'line engine cancellation checkpoints': 'Thread.currentThread().isInterrupted()' in l,
  'off-UI preview selection': 'renderController.submit(StudioRenderController.Lane.PREVIEW,' in a and 'alternatePreviewBitmap=renderTessPreview(tessellationResult,1200,index)' not in a,
  'panel LRU': 'toolPanelCache.get(panelKey)' in a and 'size()>4' in a,
}
for name,okay in checks.items():
    if not okay:raise AssertionError('V2.41 performance contract: '+name)
print('PASS: V2.41 source performance contract: GPU rendering, path cache, gesture bitmap, draft/full rendering, cancellation, preview workers, shelf cache')

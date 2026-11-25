from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
p=(root/'app/src/main/java/com/halftonestudio/ImageLevels.java').read_text()
checks={
 'stale geometry falls back to edited photo': 'if(r==null || (alternateActive() && alternateReadyGeneration!=alternateGeneration)' in s and 'drawEditedPhoto(cv,false);' in s,
 'fallback respects snip outlines and lighting': 'cv.clipOutPath(cached[i])' in s and 'Bitmap display=(showOriginal||holdCompare||tonalPreview==null)?photo:tonalPreview;' in s,
 'specialty does not require standard result': s.index('drawStandaloneSpecialty(cv,alternatePreviewBitmap);') < s.index('if(r==null || (alternateActive()'),
 'geometry publishes before gesture thumbnail': s.index('return new CanvasGeometry(result,paths);') < s.index('Bitmap buildGesturePreview()') and 'if(!draft) scheduleGesturePreview(vectorCache,id);' in s,
 'one exact snip eligibility test per input pixel': 'eligible[y*w+x]=included(s,x,y,w,h)' in p and 'eligible[ny*w+nx]' in p,
 'draft cannot export': 'standardPreviewDraft||renderedSerial!=serial' in s,
 'photo-only Prepare unchanged': 'if(activeWorkspace==0){drawPreparePhoto(cv);return;}' in s,
}
for k,v in checks.items():
    if not v: raise AssertionError('FAIL: '+k)
print('PASS v2.48.2: '+str(len(checks))+' source/view/export/caching contract checks')

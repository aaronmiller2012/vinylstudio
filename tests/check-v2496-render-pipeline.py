#!/usr/bin/env python3
"""Guard Android render-thread, Snip preview, and restore lifecycle changes."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
b=(r/'app/build.gradle').read_text()
assert 'versionCode 300' in b and '2.52.1-line-visibility-rc22' in b
assert 'previewSnipBackground()' not in s
assert 'snipFillPreview=' not in s
assert 'SnipCanvasPathCache snipPathCache' in s
assert 'snipPathCache.paths(settings.snips,ww,hh)' in s
assert 'snipPathCache.paths(drawSnips,r.settings.widthMm,r.heightMm)' in s
assert 'cv.clipPath(contours[i])' in s and 'cv.clipOutPath(contours[i])' in s
assert 'startupIo.execute(()->' in s and 'StartupPhotoLoader.load(directory)' in s
assert 'ticket!=startupRestoreSerial' in s and 'photoRevision!=expectedRevision' in s
restore=s[s.index('    private void restoreLocal(){'):s.index('    @Override protected void onStop(){')]
assert 'BitmapFactory.decodeFile(' not in restore
assert 'getPixels(' not in restore
assert 'restoreDraft(startupSavedDraft)' in restore
assert 'setWorkflowStep(startupSavedStep)' in restore
assert 'startupRestoring?startupSavedDraft:draftJson()' in s
assert 'startupRestoring?startupSavedStep:workflowStep' in s
assert s.count('cancelStartupRestore();')>=3
assert 'onTrimMemory(int level)' in s
assert 'specialtyBaseCache.clear();specialtyLitCache.clear();' in s
assert '12L*1024*1024' in s and '18L*1024*1024' in s
assert 'specialtyPrepared(' in s and 'CanvasGeometry.create(out)' in s
assert 'out.put("schema",14)' in s
assert 'VinylQuickStartGuide.isFirstInstall' in s
print('PASS v2.49.6: async guarded startup, draw-only Snip, cache budgets and memory trim')

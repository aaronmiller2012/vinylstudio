#!/usr/bin/env python3
"""RC137 guards against editor spinner lockup and stale vector export after render OOM."""
from pathlib import Path
import json
from release_version import at_least
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
info=json.loads((r/'tests/release-info.json').read_text())
assert info['versionCode'] >=415 and at_least(info['versionName'],'2.55.3')
render=s[s.index('    private void render(){'):s.index('    private void confirmExitProject(){')]
assert 'catch(RuntimeException | OutOfMemoryError failure)' in render
assert 'failure instanceof java.util.concurrent.CancellationException' in render
assert 'id!=serial||Thread.currentThread().isInterrupted()' in render
assert 'renderedSerial=-1;standardPreviewDraft=true;' in render
assert 'setRenderBusy(false);' in render
assert 'draftSourceCache.clear();preparedPixels=null;' in render
assert 'preparedSignature=Long.MIN_VALUE;' in render
assert 'specialtyBaseCache.clear();specialtyLitCache.clear();' in render
assert 'Not enough memory for this artwork' in render
assert 'VinylEngine.generate(geometryInput,vectorW,vectorH,s)' in render
assert 'final CanvasGeometry vectorCache=CanvasGeometry.create(out)' in render
prepare=s[s.index('    private void requestPreparePhotoPreview(){'):s.index('    private void render(){')]
assert 'catch(RuntimeException | OutOfMemoryError failure)' in prepare
assert 'if(token!=serial||Thread.currentThread().isInterrupted())return;' in prepare
assert 'tonalPreview=null;' in prepare
assert 'if(token==serial&&activeWorkspace==0)' in prepare
assert 'Fine-detail motif sheet:' not in s
assert 'Solid Snip silhouette supports the portrait; upper colors refine photographic detail.' in s
assert 'settings.marks=null;markIndex=0;currentMarkIssue=null;' in s
assert 'layerExtraMotif=(capMode||scaleMode)&&layeredExtraMotif;' in s
print('PASS RC137 standard/Prepare OOM handling, stale export protection, cancellation, Size/marks, clean Cell Portrait copy')

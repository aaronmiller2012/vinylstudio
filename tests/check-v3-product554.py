#!/usr/bin/env python3
"""Build 554 integration wiring that host tests cannot reach."""
import re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/halftonestudio'
s=(src/'StudioActivity.java').read_text()
lib=(src/'StudioProjectLibrary.java').read_text()
writer=(src/'StudioProjectArchiveWriter.java').read_text()
flows=(src/'StudioProductFlows.java').read_text()
gallery=(src/'ProjectGalleryDialog.java').read_text()
zipw=(src/'CutSheetZipWriter.java').read_text()
prov=(src/'SharedExportProvider.java').read_text()
# Format 2: writer always finishes with the manifest; library enforces it.
assert writer.count('finish(zip,manifest);')==2 and 'ProjectManifest.ENTRY' in writer
assert 'if(manifest!=null)manifest.verify(digests);' in lib
# Kept cut sheets: keyed to the exact edit, carried by library save, recoverable from the gallery.
assert s.count('productFlows.rememberVerifiedExport(uri,')==2
assert 'LastVerifiedExport.matching(getFilesDir(),savedRevision,savedPhotoToken)' in s and '},exportedCuts);' in s
assert 'req==EXPORT_ORIGINAL_CUTS' in s and 'projectLibrary.exportedCuts(id)' in s
# Weed score reaches the CSV and the post-export dialog.
assert 'weed_score,weed_rating' in zipw and 'WeedDifficulty.score(' in zipw and 'WeedDifficulty.hardest(' in flows
# Camera: dedicated request, no-data result handled first, write only into camera-* targets.
assert s.index('if(req==CAPTURE_PHOTO){')<s.index('if(result!=RESULT_OK||data==null||data.getData()==null){')
assert 'STATE_PENDING_CAMERA' in s and 'SharedExportFiles.isCameraCapture(uri.getPathSegments())' in prov
assert 'CAMERA' not in (root/'app/src/main/AndroidManifest.xml').read_text(), 'camera via intent must not request the CAMERA permission'
# Accessibility hooks.
assert 'ACCESSIBILITY_LIVE_REGION_POLITE' in s and 'R.id.canvas_action_reset_zoom' in s and 't.setMinHeight(dp(StudioTheme.H_PILL));' in s
# Decomposition: gallery and UI factory extracted, activity delegates.
assert 'new ProjectGalleryDialog(this,ui,new ProjectGalleryDialog.Host(){' in s and 'void show(final List<StudioProjectLibrary.Entry> saved)' in gallery
assert 'projectLibrary' not in gallery and 'private final StudioUi ui=new StudioUi(this);' in s
print('PASS 554 format-2 manifest, kept cut sheets, weed score, camera capture, accessibility and extraction wiring')

#!/usr/bin/env python3
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
lib=s.split('private void saveProjectToLibrary(String projectName,boolean saveAs,Runnable afterSaved)',1)[1].split('private void openLibraryProject',1)[0]
zip=s.split('private void writeProject(Uri uri,boolean persistable)',1)[1].split('private static boolean nativeSourceTooLargeForPortableZip',1)[0]
for body in (lib,zip):
    assert body.index('toJson(settings)')<body.index('ProjectSaveGate.begin()')
    assert 'reportProjectCaptureFailure(error)' in body
    assert 'portableOriginalSupplier(sources.nativeOriginal,originalSourceSnap)' in body
assert 'ProjectSaveSupport.optionalThumbnail' in lib
assert lib.index('ProjectSaveGate.end();gateReleased=true')<lib.index('afterSaved.run()')
assert 'if(!gateReleased)ProjectSaveGate.end();' in lib
assert 'StudioProjectLibrary.verifyProjectArchive(stage)' in zip
assert 'ProjectSaveSupport.portableOriginal(activeSource,restoredSource)' in s
preview=s.split('private Bitmap renderStandardCompositePreview(',1)[1].split('private Bitmap renderSpaceRibbonPreview(',1)[0]
assert 'currentUnderlay()' not in preview and 'silhouetteOnly()' not in preview
assert 'projectSession.setDirty(savedDirty);updateEditorSubtitle();' in s
assert 'StartupPhotoLoader.hasSavedPhoto(getFilesDir())?' not in s
print('PASS project save capture before gate, captured preview, optional original, publication order and startup dirty state')

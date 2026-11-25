#!/usr/bin/env python3
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
p=Path('app/src/main/java/com/halftonestudio/StudioProjectLibrary.java').read_text()
b=Path('app/src/main/java/com/halftonestudio/StudioProjectBackup.java').read_text()
for x in ('projectSession.isSaving()','requestProjectSwitch(', 'saveCurrentProject(open)',
          'promptProjectName(true,null)', 'EXPORT_LIBRARY_ITEM', 'EXPORT_LIBRARY_BACKUP',
          'IMPORT_LIBRARY_BACKUP', 'stableDocumentKey(', 'projectSession.setDirty(false)',
          'projectLibrary.openArchive(savedId)', 'projectThumbnails.bind(projectLibrary,id,target)',
          'showProjectLibraryWithEntries(', 'libraryJobs.list('):
    assert x in s,x
# Build 554: the gallery grid lives in ProjectGalleryDialog and binds thumbnails through its host.
assert 'host.bindThumbnail(entry.id,picture)' in Path('app/src/main/java/com/halftonestudio/ProjectGalleryDialog.java').read_text()
for x in ('revisionName(', 'move(pending,committed)', 'currentBundle(',
          'KEEP_REVISIONS=2', 'verifyArchive(', 'asset(id,".zip")', 'label.properties'):
    assert x in p,x
for x in ('exportAll(', 'importAll(', 'MAX_PROJECT_ZIP', 'ZipInputStream', 'ZipOutputStream'):
    assert x in b,x
assert 'android.useAndroidX=true' in Path('gradle.properties').read_text()
assert not Path('app/src/main/java/com/halftonestudio/StudioActivity.java.orig').exists()
print('PASS RC55 gallery and project safety wiring')

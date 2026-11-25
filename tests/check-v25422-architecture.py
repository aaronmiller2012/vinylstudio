#!/usr/bin/env python3
from pathlib import Path
root=Path('app/src/main/java/com/halftonestudio')
s=(root/'StudioActivity.java').read_text()
for f in ('StudioProjectSessionState.java','StudioLibraryJobs.java','StudioProjectThumbnails.java'):
    assert (root/f).is_file(),f
for call in ('projectSession.markEdited()','projectSession.completeSave(', 'projectSession.beginLoad()',
             'projectSession.isCurrentLoad(', 'projectSession.closeProject()',
             'libraryJobs.list(', 'libraryJobs.rename(', 'libraryJobs.delete(',
             'libraryJobs.backup(', 'libraryJobs.restore(', 'libraryJobs.exportProject(',
             'projectThumbnails.bind(', 'projectThumbnails.evictAll()'):
    assert call in s,call
for retired in ('galleryIndexExecutor','libraryThumbnailWorker','libraryThumbnails','projectSaveBusy',
                'unsavedEdits','projectLoadToken','projectThumbnail('):
    assert retired not in s,retired
assert 'versionCode '+str(__import__('json').loads(Path('tests/release-info.json').read_text())['versionCode']) in Path('app/build.gradle').read_text()
assert 'android.useAndroidX=true' in Path('gradle.properties').read_text()
assert 'projectLibrary.save(' in s and 'StudioProjectArchiveWriter.write(' in s
assert 'requestProjectSwitch(' in s and 'saveCurrentProject(open)' in s
assert 'Dot-to-Dot' not in s and 'dot-to-dot' not in s.lower()
for engine in ('VinylEngine.java','LineArtEngine.java','MultilayerStencilEngine.java','SpiralPortraitRibbon.java'):
    assert (root/engine).is_file(),engine
print('PASS RC56 extraction wiring, versioning and retained styles')

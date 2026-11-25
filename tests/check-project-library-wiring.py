#!/usr/bin/env python3
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
p=Path('app/src/main/java/com/halftonestudio/StudioProjectLibrary.java').read_text()
for phrase in ('showProjectLibrary()', 'saveCurrentProject()', 'openLibraryProject(',
               'StudioProjectThumbnails.encode(', 'loadProject(uri,null,null)',
               'currentLibraryId=openedId', 'menu.getMenu().add("Project library")',
               'menu.getMenu().add("Export project ZIP")',
               'projectLibrary.save(', 'gallery.getWindow().setLayout(-1,-1)',
               'ProjectArchiveReader.read(zip)', 'StudioProjectArchiveWriter.write(zip,'):
    assert phrase in s, phrase
assert 'android.useAndroidX=true' in Path('gradle.properties').read_text()
assert 'dot-to-dot' not in s.lower()
assert 'move(pending,committed)' in p and 'currentBundle(id)' in p
assert 'private void showProjectLibrary()' in s
print('PASS project gallery wiring and archive compatibility')

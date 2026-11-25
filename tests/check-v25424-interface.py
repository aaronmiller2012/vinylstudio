#!/usr/bin/env python3
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
m=Path('app/src/main/java/com/halftonestudio/StudioProjectGalleryModel.java').read_text()
b=Path('app/build.gradle').read_text()
import json
meta=json.loads(Path('tests/release-info.json').read_text())
assert meta['versionCode']>=336 and meta['versionName'] in b
assert 'void showArtTechniquesDialog()' in s
picker=s[s.index('private void showSingleLinePickerDialog()'):s.index('private void showArtTechniquesDialog()')]
assert 'VinylStyleSuite.TSP' not in picker and 'VinylStyleSuite.LINOCUT' not in picker
art=s[s.index('private void showArtTechniquesDialog()'):s.index('private final class SingleLineSample')]
for name in ('VinylStyleSuite.TSP','VinylStyleSuite.LINOCUT','VinylStyleSuite.TRUCHET','VinylStyleSuite.RIDGE'):
    assert name in art,name
assert 'VinylStyleSuite.TEXT' not in art, 'RC60 migrates text into Multilayer'
assert 'Search projects or styles' in s
assert 'projectThumbnails.bind(projectLibrary,entry.id,picture)' in s
assert 'StudioProjectGalleryModel.visible(saved,search.getText().toString(),sorting[0])' in s
assert 'gallerySort' in s and 'getPreferences(MODE_PRIVATE).edit().putInt("gallerySort",selected).apply()' in s
assert 'Backup all projects' in s and 'Restore backup ZIP' in s
assert 'dp(48)' in s
assert 'columnsForWidth(getResources().getConfiguration().screenWidthDp)' in s
assert 'showProjectLibraryWithEntries(entries)' in s
assert 'requestProjectSwitch(' in s
assert 'class StudioProjectGalleryModel' in m
assert 'Dot-to-Dot' not in s
print('PASS RC58 gallery integration, categories, backup, compatibility checks')

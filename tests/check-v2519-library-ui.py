from pathlib import Path
root=Path(__file__).resolve().parents[1]
a=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
for expected in (
 'promptSaveNamedPalette()', 'readSavedPalettes()', 'writeSavedPalettes(',
 'confirmDeleteSavedPalette(', 'savedPaletteLibraryPanel()',
 'new AlertDialog.Builder(this).setTitle("Save vinyl palette")',
 'new AlertDialog.Builder(this).setTitle("Delete saved palette?")',
 'SavedVinylPaletteLibrary.saveFirst(', 'SavedVinylPaletteLibrary.remove(',
 'getString("favorite",null)', 'PALETTE_LIBRARY_KEY="named_library_v2"',
 'putString(PALETTE_LIBRARY_KEY,encoded.toString()).commit()',
 'if(tessellationMode||layeredCellMode)',
 ):
 assert expected in a,expected
assert a.index('savedPaletteLibraryPanel();')<a.index('section(presets,"Vinyl palettes')
assert 'saveFavoriteVinylPalette()' not in a
assert 'loadFavoriteVinylPalette()' not in a
assert 'versionCode 300' in (root/'app/build.gradle').read_text()
print('PASS saved palette UI dialogs, newest-first panel, preference migration, delete, version')

#!/usr/bin/env python3
"""RC76: one Reverse control for the active palette; tap tile to apply instantly."""
from pathlib import Path
import json,re,hashlib
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
i=json.loads((r/'tests/release-info.json').read_text())
g=(r/'app/build.gradle').read_text();b=(r/'BUILD_ON_NAS.sh').read_text()
assert i['versionCode'] in (354,355,356,357,358,359,360,361,362,363,364) and i['gate'] in b

assert i['versionName'] in g and 'versionCode '+str(i['versionCode']) in g
assert i['gate'] in b and i['apk'] in b
start=s.index('private void inlinePaletteTile(');end=s.index('private void inlinePaletteGallery(',start)
tile=s[start:end]
assert 'tile.setOnClickListener(v->applyInlinePalette(key,true))' in tile
assert 'if(selected){' in tile and 'togglePaletteOrientation(key)' in tile
assert tile.count('action(')==1, 'exactly one conditional Reverse control per selected tile'
assert 'TextView apply=action(' not in tile and 'action(actions' not in tile
assert 'reversedPalette(key)?"Normal orientation":"Reverse palette"' in tile
assert 'selected?ACCENT' in tile
saved=s[s.index('private void savedPaletteLibraryPanel('):s.index('private void colorPaletteOnlyPanel(')]
assert 'item.setOnClickListener(v->applySavedPalette(entry))' in saved
assert 'TextView load=action(' not in saved and '"Apply"' not in saved
assert 'if(selected){' in saved and 'togglePaletteOrientation(savedKey)' in saved
assert 'confirmDeleteSavedPalette(entry)' in saved
orient=s[s.index('private void togglePaletteOrientation('):s.index('private void invalidatePreparedPalette(')]
assert 'key.startsWith("saved:")' in orient and 'readSavedPalettes()' in orient
assert 'if(key.equals(selectedPaletteKey))' in orient
assert 'paletteReversed.put(key,!reversedPalette(key))' in orient
assert 'selectedPaletteKey=key' in s[s.index('private void applyInlinePalette('):s.index('private void setCmyk(')]
assert 'selectedPaletteKey=key' in s[s.index('private void applySavedPalette('):s.index('private void confirmDeleteSavedPalette(')]
assert 'out.put("selectedPaletteKey",selectedPaletteKey)' in s
assert '"paletteReversed"' in s
# Keep the prior Layers crash fix intact; do not regress unrelated geometry.
assert 'private LinearLayout group(LinearLayout parent)' in s
for name in ('VinylEngine.java','LineArtEngine.java','MultiColorStippleEngine.java','StyleKit.java'):
 assert (r/'app/src/main/java/com/halftonestudio'/name).is_file()
print('PASS RC76 tap-to-apply tiles, one selected Reverse, saved palette selection, persistence, release metadata')

#!/usr/bin/env python3
"""Source-level UI and metadata wiring checks complement behavioral Java tests."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
m=json.loads((r/'tests/release-info.json').read_text())
assert tuple(map(int,m['version'].split('.'))) >= (2,54,66) and m['versionCode']>=378
assert m['gate'].startswith('tests/run-v254') and m['gate'].endswith('-gate.sh')
assert m['apk'].startswith('VinylHalftoneStudio-v'+m['version']) and m['apk'].endswith('.apk')
assert m['apk'] in (r/'BUILD_ON_NAS.sh').read_text()
assert m['versionName'] in (r/'app/build.gradle').read_text()
assert 'activeVinylSwatchesPanel();' in s
assert 'PaletteRecommendations.choose(family,preparedPhotoPalette,' in s
assert 'savedPaletteLibraryPanel();' in s
assert 'showAllBuiltInPalettes' in s
assert '"All 26 built-in palettes ›"' in s
assert 'PaletteStylePolicy.arrange' in s
assert 'specialtyProcessColors()' in s
assert 'PaletteStylePolicy.familyForNewStyle(newStyleId)' in s
assert 'private void newStyleColorsPanel()' in s and 'private void colorPaletteOnlyPanel()' in s
assert 'TYPOGRAPHY_PORTRAIT' not in (r/'app/src/main/java/com/halftonestudio/VinylStyleSuite.java').read_text()
assert not list((r/'app/src/main/java/com/halftonestudio').glob('Typography*.java'))
assert (r/'app/src/main/java/com/halftonestudio/PaletteStylePolicy.java').is_file()
assert (r/'app/src/main/java/com/halftonestudio/PerceptualVinylColor.java').is_file()
assert (r/'app/src/main/java/com/halftonestudio/PaletteRecommendations.java').is_file()
print('PASS RC100: active-color UI, compact style rankings, complete catalog, saved palettes, output/source wiring, typography remains removed')

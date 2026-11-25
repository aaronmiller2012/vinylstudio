#!/usr/bin/env python3
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=347 and meta['gate'] in ('tests/run-v25435-colors-gate.sh','tests/run-v25436-export-colors-gate.sh','tests/run-v25437-solid-snip-gate.sh','tests/run-v25438-auto-silhouette-gate.sh','tests/run-v25439-silhouette-l1-gate.sh','tests/run-v25440-physical-layers-gate.sh','tests/run-v25441-visible-controls-gate.sh','tests/run-v25442-palette-tap-gate.sh','tests/run-v25443-cut-sheet-gate.sh','tests/run-v25444-reliability-gate.sh','tests/run-v25445-glass-portrait-gate.sh','tests/run-v25446-glass-cutfix-gate.sh','tests/run-v25447-palette-harmony-gate.sh','tests/run-v25448-integrity-gate.sh','tests/run-v25449-guide-gate.sh','tests/run-v25450-performance-gate.sh','tests/run-v25451-security-gate.sh')
assert 'newStylePalettePicker);' not in s[s.index('private void newStyleColorsPanel()'):s.index('private void newStyleLayersPanel()')]
assert 'inlinePaletteGallery(group());' in s
assert 'vinylPaletteHueControl(group());' in s
assert 'settings.marks=null;markIndex=0;currentMarkIssue=null;' in s
surface=s[s.index('private void selectMountingSurface('):s.index('private void usePreset(')]
assert 'applyActivePresetColors' not in surface and 'refreshTessPaletteForCount' not in surface
assert 'preparedPhotoPalette=​suggested' not in s
assert 'preparedPhotoPalette=suggested' in s
assert '"paletteReversed"' in s and '"preparedPhotoPalette"' in s
assert 'PaletteOrientation.orientCmyk' in s and 'ensurePreparedPhotoPalette()' in s
print('PASS RC69 inline palettes/hue, photo tile, orientation storage and physical resize reset')

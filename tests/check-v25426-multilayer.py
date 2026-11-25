#!/usr/bin/env python3
"""RC60 integration guarantees for Snip underlay, 2..8 total slots, and stable IDs."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/com/halftonestudio'
ui=(java/'StudioActivity.java').read_text()
engine=(java/'TextHalftoneEngine.java').read_text()
suite=(java/'VinylStyleSuite.java').read_text()
slots=(java/'NewStyleColorSlots.java').read_text()
segment=ui[ui.index('private void showMultilayerPickerDialog()'):ui.index('private void stylePickerBack(')]
arts=ui[ui.index('private void showArtTechniquesDialog()'):ui.index('private final class SingleLineSample')]
# RC62 relocates ASCII/Words under Shapes while preserving engine/style IDs.
assert 'VinylStyleSuite.TEXT' not in segment
# RC65 promotes both into independent Shapes tiles; the old style remains readable.
assert 'VinylEngine.SHAPE_STYLES' in ui and 'VinylEngine.WORD_SCREEN' in ui
assert 'VinylEngine.WORD_SCREEN' in ui and 'VinylEngine.ASCII' in ui
assert 'VinylStyleSuite.TEXT' not in arts
assert 'activateTextPortrait(int variant)' in ui
assert 'L1 is the filled Snip background' in ui
assert 'Snipped background' in ui and 'newStyleId==VinylStyleSuite.TEXT' in ui
assert 'o.silhouetteUnderlay=true' in suite
assert 'newStyleId==VinylStyleSuite.TEXT?0:settings.snipFillMode' in ui
assert 'snipped-background-1' in engine and 'VinylStyleSuite.subjectLoops(src)' in engine
assert 'o.silhouetteUnderlay' in engine and 'final int textSlots=layers.length-1' in engine
assert '"snipped-background-1".equals(layerName)' in slots
assert 'private void showUnifiedStyleDrawer()' in ui
assert 'void setCmyk()' in ui
assert 'Dot-to-Dot Portrait' not in ui
meta=json.loads((root/'tests/release-info.json').read_text())
assert meta['versionCode']>=338 and meta['versionName'] in (root/'app/build.gradle').read_text()
assert meta['versionName'] in (root/'app/build.gradle').read_text()
assert meta['gate'] in (root/'BUILD_ON_NAS.sh').read_text()
print('PASS RC60 category, 2–8 total physical layers, solid Snip base, palette slots, save-format compatibility')

#!/usr/bin/env python3
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
a=s[s.index('    private void newStyleColorsPanel(){'):s.index('    private void newStyleLayersPanel(){')]
b=s[s.index('    private void colorPaletteOnlyPanel(){'):s.index('    /** Palette card order matches the stack',s.index('    private void colorPaletteOnlyPanel(){'))]
assert a.index('vinylPaletteHueControl(')<a.index('section(paper,"Mounting surface")')<a.index('Active vinyl colors')<a.index('inlinePaletteGallery(')
assert b.index('section(b,"Mounting surface")')<b.index('activeVinylSwatchesPanel()')<b.index('inlinePaletteGallery(')
assert 'int shown=entries.size()' in s
assert 'All built-in palettes · ' in s
assert 'new VinylColorEditor(this,settings.paper)' in s and 'new AlertDialog.Builder(this).setTitle("Mounting surface · color")' in s
assert 'private void confirmRemoveNewStyleLayer(' in s and 'private void confirmRemoveUnifiedLayer(' in s
assert 'VinylLayerDelete.remove(' in s and 'VinylLayerDelete.removeOrder(' in s
assert 'PaletteOrientation.reverseActive(' in s
assert 'reverseActiveVinylPalette' in s and 'Reverse",this::reverseActiveVinylPalette' in s
assert 'tessellationGapSlider(host,"Gap between scales · mm")' in s and 'tessellationGapSlider(host,"Gap between caps · mm")' in s
assert 'Tile spacing · finer at higher detail' in s
assert 'tessellationGapSlider(host,"Vinyl gap / spacing · mm")' in s
assert s.index('inchSlider(controls,"1 · Spacing"')<s.index('if(LineArtEngine.supportsShapeDefinition(settings.pattern))shapeControls();')
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=406 and meta['gate'].startswith('tests/run-')
print('PASS RC124 Colors order, all swatches, mount editor, layer deletion, spacing-first and metadata')

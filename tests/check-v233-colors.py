from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
g=(r/'app/build.gradle').read_text()
assert version_code_matches(g) and version_name_matches(g)
for method in ['colorPaletteOnlyPanel','colorSwatchGrid','layerCountPicker','restoreParkedVinylLayers','applyTonalPalette','applyTessPalette']:
    assert 'private void '+method+'(' in s,method
assert 'case 2:colorPaletteOnlyPanel();break;' in s
assert 'case 3:unifiedLayersPanel();break;' in s
assert 'if(continuousStyle!=null||mazePortraitMode)' in s
assert 'ColorWorkspacePalette.columns(colors.length)' in s
assert 'new LinearLayout.LayoutParams(0,dp(73),1)' in s
assert 'swatch.setMinHeight(dp(48))' in s
assert 'ColorWorkspacePalette.selectForSurface(VinylPalettes.get(id,8),settings.layers.size(),settings.paper)' in s
assert 'Source-derived colors' in s[s.index('private void colorPaletteOnlyPanel'):s.index('private int[] presetTessSwatches(')]
assert 'sourceColorMode=true' in s and 'options.colors=tonalColorOverrides.clone()' in s
assert 'selectUnifiedLayer(idx)' in s and 'private void selectTonalLayer(int index)' in s and 'private void selectTessLayer(int index)' in s
assert 'out.put("parkedVinylLayers",parkedJson)' in s
assert 'restoreParkedVinylLayers(restored)' in s and 'restoreParkedVinylLayers(projectJson)' in s
assert 'private void showLegacyStyles()' not in s
assert 'vinyl matching' not in s[s.index('private void colorPaletteOnlyPanel'):s.index('private int contrastText')]
assert 'private void showUnifiedStyleDrawer()' in s
print('PASS: V2.33 source routes all swatches to the active style; counted grids, palette scope, saved inactive layers, compatibility styles')

from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
for term in ['vinylPaletteHueControl(b)','"Vinyl palette hue · all active swatches"',
             '"Mounting surface"','tessHueBaseColors', 'tessHueDegrees', 'applyTessHue(',
             'tessellationHueBaseColors','tessellationHueDegrees','preserveManualTessColor(',
             'setTessBasePalette(suggested)', 'setTessBasePalette(palettes[which])',
             'private void vinylPaletteHueControl(', 'requestAlternate();', '"Reset hue to 0°"']:
    assert term in s,term
assert 'if(continuousStyle==null&&!mazePortraitMode)vinylPaletteHueControl(b);' in s
assert s.index('vinylPaletteHueControl(b)')<s.index('if(tonalStencilMode){', s.index('private void colorPaletteOnlyPanel()'))
assert ('versionCode 294' in (r/'app/build.gradle').read_text() or ('versionCode 295' in (r/'app/build.gradle').read_text() or ('versionCode 296' in (r/'app/build.gradle').read_text() or 'versionCode 300' in (r/'app/build.gradle').read_text())))
assert 'VinylHalftoneStudio-v2.52.1-LineVisibility-RC22.apk' in (r/'BUILD_ON_NAS.sh').read_text()
# The protected generators must not be modified for cosmetic palette recoloring.
print('PASS v2.51.4 UI: hue under mounting surface, reversible saved baseline, manual colors and release wiring')

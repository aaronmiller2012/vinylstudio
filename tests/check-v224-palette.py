from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
p=Path('app/src/main/java/com/halftonestudio/VinylPalettes.java').read_text()
assert '"Prepare","Shapes","Adjust","Colors","Layers","Export"' in s
assert '"Styles","Adjust","Curves"' not in s
assert 'section(c,"Selected vinyl layer")' not in s
assert '"Visible-only ON"' not in s and '"Visible-only OFF"' not in s
assert 's.substrateAware=true;' in s and 'out.put("substrateAware",true)' in s
assert 'cmykPaletteTile(paletteRow)' in s
assert 'Desert Clay' in p and 'Arctic Blue' in p and 'Vintage Print' in p and 'Wildflower' in p
assert 'alternatePreviewBitmap' in s and 'renderController.submit(StudioRenderController.Lane.SPECIALTY,' in s
print('PASS: V2.24 CMYK swatch, expanded palettes, curves hidden, visible-only mandatory, single-line render hotfix')

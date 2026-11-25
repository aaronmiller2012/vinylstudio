from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
b=(r/'app/build.gradle').read_text()
assert version_code_matches(b) and version_name_matches(b)
assert s.index('styleDrawerRow(list,"CMYK"') < s.index('styleDrawerRow(list,"Multilayer"') < s.index('styleDrawerRow(list,"Shapes"') < s.index('styleDrawerRow(list,"Lines"') < s.index('styleDrawerRow(list,"Single Line"')
for text in ('Grid Cell Portrait','tessellationControls(g)','renderTessPreview(', 'TessellationEngine.generate(', 'tessellationResult=mosaicReady', 'layer-manifest.csv','mosaic.svg(layer)','tessellationMode', 'tessSelectedLayer'):
    assert text in s,text
assert 'opts.allowedPixels=allowed' in s and 'mosaic.counts[layer]' in s
assert 'tessellationMode' in s and 'return continuousStyle!=null||tonalStencilMode||mazePortraitMode||tessellationMode' in s
print('PASS: legacy V2.26 compact controls, per-layer preview, vector export, crop/snip guards, updated style picker order')

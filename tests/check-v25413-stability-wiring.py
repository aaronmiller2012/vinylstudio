from pathlib import Path
import json
p=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
a=Path('app/src/main/java/com/halftonestudio/VinylStyleSuite.java').read_text()
meta=json.loads(Path('tests/release-info.json').read_text())
assert 'MaskedFacetClipper.intersect(poly,src)' in a
assert 'checkpoint();rememberNewStylePalette();newStyleId=-1' in p
assert 'StyleCutGeometryPreflight.inspect(result' in p
assert 'StyleCutGeometryPreflight.inspect((StyleKit.Result)source' in p
assert 'NewStyleColorSlots.slot(style,layer.name,i)' in p
assert 'newStyleDetailMemory[id]' in p and 'newStyleSettingsSaved[id]' in p
assert 'cachedNewStyleMarks(artW,artH)' in p
assert meta['versionName'] in Path('app/build.gradle').read_text()
assert meta['gate'] in Path('BUILD_ON_NAS.sh').read_text()
assert meta['apk'] in Path('BUILD_ON_NAS.sh').read_text()
assert meta['gate'] in Path('BUILD_ON_NAS.sh').read_text()
print('PASS: clipping, layer identity, Undo, settings, risk markers, mark cache, release wiring')

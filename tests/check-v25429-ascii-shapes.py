#!/usr/bin/env python3
from release_version import at_least
"""Source contract: a new Shapes motif, not a replacement for saved text IDs."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
d=r/'app/src/main/java/com/halftonestudio'
engine=(d/'VinylEngine.java').read_text()
ui=(d/'StudioActivity.java').read_text()
ascii=(d/'AsciiScreenMotif.java').read_text()
info=json.loads((r/'tests/release-info.json').read_text())
assert info['versionCode']>=341 and at_least(info['versionName'], '2.54.0')
assert info['versionName'] in (r/'app/build.gradle').read_text()
assert info['gate'] in (r/'BUILD_ON_NAS.sh').read_text()
assert 'ASCII=27' in engine and 'DASH,ASCII' in engine
assert 'final AsciiScreenMotif ascii=style==ASCII' in engine
assert 'coverage(p,r.settings,li)' in engine
assert 'showShapePickerDialog()' in ui
assert 'VinylEngine.ASCII' in ui and 'VinylEngine.SHAPE_STYLES' in ui
assert 'activateNewStyle(VinylStyleSuite.TEXT)' in ui
assert 'newStyleVariant=variant' in ui
assert 'AsciiVectorGlyphs' in ascii
assert 'StyleKit.mergeTriangles' in (d/'AsciiVectorGlyphs.java').read_text()
assert 'if(!r.settings.snips.allowsPolygon(ring' in engine
assert 'android.useAndroidX=true' in (r/'gradle.properties').read_text()
assert not list(d.glob('*DotToDot*'))
print('PASS RC63 style ID, existing screening, physical size, Snip contour checks, legacy IDs, AndroidX')

#!/usr/bin/env python3
from release_version import at_least
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
d=r/'app/src/main/java/com/halftonestudio'
s=(d/'AsciiScreenMotif.java').read_text()
v=(d/'AsciiVectorGlyphs.java').read_text()
e=(d/'VinylEngine.java').read_text()
i=json.loads((r/'tests/release-info.json').read_text())
assert i['versionCode']>=342 and at_least(i['versionName'], '2.54.0')
assert i['gate'].startswith('tests/run-') and i['gate'].endswith('-gate.sh')
assert i['versionName'] in (r/'app/build.gradle').read_text()
assert i['gate'] in (r/'BUILD_ON_NAS.sh').read_text()
assert 'ASCII=27' in e and 'DASH,ASCII' in e
assert 'final AsciiScreenMotif ascii=style==ASCII' in e
assert 'coverage(p,r.settings,li)' in e
assert 'AsciiVectorGlyphs.shape(' in s and 'glyph.fits(footprint,minimumMm)' in s
assert 'StyleKit.mergeTriangles' in v and 'MAX_TEMPLATES=256' in v
assert 'float asciiCarry=0f' in e and 'ascii.accepted(row)' in e
assert 'style!=ASCII&&diameter<min' in e
assert 'android.useAndroidX=true' in (r/'gradle.properties').read_text()
assert not list(d.glob('*DotToDot*'))
print('PASS RC64 stable style ID, shared CMYK, cut-size glyphs, bounded residual, AndroidX')

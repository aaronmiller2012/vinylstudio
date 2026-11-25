from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(r/'app/src/main/java/com/halftonestudio/VinylEngine.java').read_text()
p=(r/'app/src/main/java/com/halftonestudio/SmartHalftonePresets.java').read_text()
g=(r/'app/build.gradle').read_text()
assert version_code_matches(g) and version_name_matches(g)
assert 'private void showShapePickerDialog()' in s
assert 'private void showLinesPickerDialog()' in s
assert 'styleDrawerRow(list,"Lines"' in s
for removed in ('shapeWaviness','shapeWaveFrequency','warpedCenter','waveClearanceScale','Waviness · %','Wave scale · cycles/inch'):
    assert removed not in s,('removed UI control remains',removed)
    assert removed not in e,('removed engine logic remains',removed)
    assert removed not in p,('removed preset remains',removed)
assert 'settings.waveAmplitude' in s and 'case VinylEngine.WAVE' in p
assert 'float lim=sp-s.bridgeMm' in e
assert 'float x=(float)(s.widthMm/2+u*ca-v*sa)' in e
assert 'float y=(float)(r.heightMm/2+u*sa+v*ca)' in e
print('PASS: shape waviness controls, geometry and persistence removed; Wavy Lines unaffected')

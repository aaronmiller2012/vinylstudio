from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/com/halftonestudio'
s=(p/'StudioActivity.java').read_text()
c=(p/'ContinuousLineModes.java').read_text()
d=(p/'ProceduralPortraitLines.java').read_text()
g=(r/'app/build.gradle').read_text()
assert 'ADAPTIVE_HILBERT, TONAL_SPIROGRAPH' in c
assert 'ProceduralPortraitLines.generate(' in c
assert '"Adaptive Hilbert"' in s
assert 'tonalStencilMode=true;' in s
assert 'PhysicalFeatureRules.minimumIslandPixels' in s
assert 'ContinuousToolpathValidator.check' in s
assert 'renderToken!=alternateGeneration' in s
assert version_code_matches(g) and version_name_matches(g)
assert 'path.moveTo' in d or 'path.moveTo' in (p/'ContinuousLineModes.java').read_text()
assert 'out.add(x,y)' in d
print('PASS: V2.18 all specialty styles selectable, physical cleanup and export guards wired')

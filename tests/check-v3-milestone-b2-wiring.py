#!/usr/bin/env python3
"""Static binding checks; Android type compilation remains a separate NAS task."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]
source=(p/'app/src/main/java/com/halftonestudio/VinylEngine.java').read_text()
sampler=(p/'app/src/main/java/com/halftonestudio/ScaleAwarePhotoSampler.java').read_text()
lines=(p/'app/src/main/java/com/halftonestudio/LineArtEngine.java').read_text()
assert source.count('r.photoSampler.detailPreservingMotifCoverage(')==1
assert 'public float detailPreservingMotifCoverage(' in sampler
assert 'if(!inside(x,y)||(argb[iy(y)*width+ix(x)]>>>24)<16)return 0f;' in sampler
assert 'center<=.0001f' in sampler
assert 'detailPreservingMotifCoverage(' not in lines, 'Ribbon carrier geometry must retain known-good sampler'
assert 'r.photoSampler.motifCoverage(' not in source, 'Shapes must use B2 sampler'
build=(p/'BUILD_ON_NAS.sh').read_text()
ci=(p/'.github/workflows/verify.yml').read_text()
gradle=(p/'app/build.gradle').read_text()
for name,s in [('NAS',build),('CI',ci),('Gradle',gradle)]:
    assert 'tests/run-v3-milestone-b2-gate.sh' in s, name+' has not made B2 mandatory'
print('PASS B2 wiring: Shapes only; safe alpha/blank guards, no new LineArtEngine path, mandatory NAS/CI/Gradle gates')

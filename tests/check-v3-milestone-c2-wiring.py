#!/usr/bin/env python3
"""Guard against accidental restoration of per-piece preview allocations or missing gates."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]
s=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'BoundedBinaryMaskPreview.compose(' in s
assert 'Bitmap.createScaledBitmap(bmp,' not in s[s.index('private Bitmap renderSpaceRibbonPreview('):s.index('private Bitmap renderAlternatePreviewBitmap(')]
for method, next_method in [('renderLayeredPreview(', 'private static void addLayeredRing('),
                            ('renderTessPreview(', 'private Bitmap renderTessCollisionPreview('),
                            ('renderStandardCompositePreview(', 'private Bitmap renderSpaceRibbonPreview(')]:
    begin=s.index('private Bitmap '+method)
    end=s.index(next_method,begin)
    block=s[begin:end]
    if method.startswith('renderStandard'):
        assert 'Path path=reusableShapePath;path.rewind()' in block
    else:
        assert 'path.rewind();path.setFillType(Path.FillType.EVEN_ODD)' in block
        assert block.count('new Path()')==1, method
    assert 'CancellationCheckpoint.check();' in block
for file in ('BUILD_ON_NAS.sh','app/build.gradle','.github/workflows/verify.yml'):
    assert 'tests/run-v3-milestone-c2-gate.sh' in (p/file).read_text(), file
print('PASS C2 preview memory, per-shape Path reuse, cancellation and mandatory NAS/Gradle/CI gating')

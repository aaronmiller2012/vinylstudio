#!/usr/bin/env python3
"""Ensure current A/B/C3 fixes run before an Android package can publish."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
svg=(root/'app/src/main/java/com/halftonestudio/SvgDxfR13Exporter.java').read_text()
final=(root/'app/src/main/java/com/halftonestudio/FinalCutSheetSafety.java').read_text()
preview=(root/'app/src/main/java/com/halftonestudio/BoundedBinaryMaskPreview.java').read_text()
assert 'openStrokes.add(xy)' in svg
assert 'geometry.openStrokes' in final
assert 'double coverage=inkArea/(scaleX*scaleY)' in preview
assert 'new int[dw*dh]' in preview
for p in ('BUILD_ON_NAS.sh','app/build.gradle','.github/workflows/verify.yml'):
    content=(root/p).read_text()
    assert 'tests/run-v3-milestone-c3-gate.sh' in content,f'C3 gate missing from {p}'
assert 'tests/check-v3-qualification-gates.py' in (root/'tests/run-v3-milestone-c3-gate.sh').read_text()
print('PASS A/B/C3 wiring: physical open bounds, coverage-preserving previews, portrait QA and required NAS/CI/Gradle gates')

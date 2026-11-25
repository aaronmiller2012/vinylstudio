#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/com/halftonestudio'
line=(p/'LineArtEngine.java').read_text()
repair=(p/'LineRibbonRepair.java').read_text()
zipwriter=(p/'CutSheetZipWriter.java').read_text()
assert 'LineRibbonRepair.safeOutline(run,r.settings.minimumMm)' in line
assert 'style==VinylEngine.LINE||style==VinylEngine.CONTOUR' in line
assert 'return original;' in repair and 'MAX_PAIR_TESTS' in repair
assert 'cut-complexity.csv' in zipwriter
assert 'outputDeviceId==OutputDeviceProfile.CRICUT && elements>5000L' in zipwriter
for file in ['BUILD_ON_NAS.sh','app/build.gradle','.github/workflows/verify.yml']:
    assert 'tests/run-v3-milestone-ab-wrap-c1-gate.sh' in (r/file).read_text(),file
print('PASS A5/B/C1 mandatory NAS, Gradle and CI wiring')

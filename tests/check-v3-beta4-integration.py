#!/usr/bin/env python3
from pathlib import Path
import json
import re
r=Path(__file__).resolve().parents[1]
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=455
assert re.fullmatch(r'VinylHalftoneStudio-v' + re.escape(meta['versionName']) + r'-[A-Za-z0-9][A-Za-z0-9._-]*\.apk', meta['apk']), 'APK name must match active version, not a legacy Beta label'
source=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert source.count('new CutSheetZipWriter(zip,targetForThisExport,')==3 and source.count('new CutSheetZipWriter(zip,target,')==1
assert (r/'app/src/main/java/com/halftonestudio/FinalCutSheetSafety.java').exists()
assert 'FinalCutSheetSafety.Report report=FinalCutSheetSafety.inspect' in (r/'app/src/main/java/com/halftonestudio/CutSheetZipWriter.java').read_text()
assert 'PhotoRevisionStore.commit' in (r/'tests/V3Beta4ProcessDeathTest.java').read_text()
assert (r/'tests/V3Beta4CutterFixture.java').exists()
assert (r/'app/src/test/java/com/halftonestudio/V3Beta4UnitTest.java').exists()
assert (r/'scripts/QUALIFY_ANDROID_DEVICE.sh').exists()
assert 'tests/run-v3-beta4-host-gate.sh' in (r/'BUILD_ON_NAS.sh').read_text()
assert 'tests/run-v3-beta4-host-gate.sh' in (r/'.github/workflows/verify.yml').read_text()
assert 'tests/run-v3-milestone-a1-gate.sh' in (r/'BUILD_ON_NAS.sh').read_text()
assert 'tests/run-v3-milestone-a1-gate.sh' in (r/'.github/workflows/verify.yml').read_text()
assert 'tests/run-v3-milestone-a2-gate.sh' in (r/'BUILD_ON_NAS.sh').read_text()
assert 'tests/run-v3-milestone-a2-gate.sh' in (r/'.github/workflows/verify.yml').read_text()
for relative in ('BUILD_ON_NAS.sh','transfer/PHONE_BUILD_AND_RECEIVE.sh','transfer/PHONE_RECEIVE_ONLY.sh'):
 assert meta['apk'] in (r/relative).read_text(),relative
print('PASS v3 Beta4 wiring, export coverage, test and release metadata')

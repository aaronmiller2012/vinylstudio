#!/usr/bin/env python3
"""Correctness/integration contracts, not a substitute for Android compilation."""
from pathlib import Path
import json
from release_gate_graph import gate_reachable
r=Path(__file__).resolve().parents[1]
release=json.loads((r/'tests/release-info.json').read_text())
assert release['version'].startswith('3.') and release['versionCode']>=453
assert release['gate'].startswith('tests/run-v3-'), 'Unexpected V3 release gate path'
# New style QA runs alongside the full inherited v3 host gate in both actual
# build entry points; don't force the style-specific gate to re-run the entire
# historical suite in order to satisfy a static release contract.
for entry in ('BUILD_ON_NAS.sh', 'BUILD_PLAY_BUNDLE_ON_NAS.sh'):
    assert gate_reachable(r, entry, release['gate']), f'Configured gate not reached by {entry}'
    assert gate_reachable(r, entry, 'tests/run-v3-host-gate.sh'), f'Inherited v3 gate not reached by {entry}'
assert ('v'+release['version']+'-') in release['apk']
assert release['gate'] in (r/'BUILD_ON_NAS.sh').read_text()
assert release['gate'] in (r/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()
assert release['gate'] in (r/'.github/workflows/verify.yml').read_text()
assert release['gate'] in (r/'app/build.gradle').read_text()
assert release['apk'] in (r/'BUILD_ON_NAS.sh').read_text()
assert release['apk'] in (r/'transfer/PHONE_BUILD_AND_RECEIVE.sh').read_text()
assert release['apk'] in (r/'transfer/PHONE_RECEIVE_ONLY.sh').read_text()
assert "applicationId 'com.halftonestudio'" in (r/'app/build.gradle').read_text()
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'ProjectArchiveReader.readToFiles(zip,getCacheDir())' in s
assert 'ownership.own(project::close)' in s
assert s.index('project.close();',s.index('ProjectArchiveReader.readToFiles(zip,getCacheDir())')) < s.index('ownership.transferToUi()',s.index('ProjectArchiveReader.readToFiles(zip,getCacheDir())'))
assert 'PhotoDecodeBudget.plan(probe.outWidth,probe.outHeight' in s
assert 'ProjectTempJanitor.prune(scratch,System.currentTimeMillis())' in s
assert ':app:testDebugUnitTest :app:lintDebug :app:assembleDebug' in (r/'BUILD_ON_NAS.sh').read_text()
assert 'validateReleasePrivacy' in (r/'app/build.gradle').read_text()
assert 'VINYL_PRIVACY_CONTACT' in (r/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()
assert (r/'app/src/test/java/com/halftonestudio/V3ProjectStreamingUnitTest.java').is_file()
assert 'vars.VINYL_PRIVACY_URL !=' in (r/'.github/workflows/verify.yml').read_text()
print('PASS v3 identity, privacy isolation, disk streaming, cleanup, pixel budget, CI/phone/NAS wiring')

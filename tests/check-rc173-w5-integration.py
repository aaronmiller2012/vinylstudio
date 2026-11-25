#!/usr/bin/env python3
from pathlib import Path
import json,re
from release_gate_graph import gate_reachable
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
g=(r/'app/src/main/java/com/halftonestudio/StyleCutGeometryPreflight.java').read_text()
o=(r/'app/src/main/java/com/halftonestudio/ProjectLoadOwnership.java').read_text()
release=json.loads((r/'tests/release-info.json').read_text())
# The current style gate and inherited W5 gate may be siblings in a release
# pipeline. Require both to be executable from debug NAS and signed Play entry.
for entry in ('BUILD_ON_NAS.sh', 'BUILD_PLAY_BUNDLE_ON_NAS.sh'):
    assert gate_reachable(r, entry, release['gate']), f'Current gate missing from {entry}'
    assert gate_reachable(r, entry, 'tests/run-rc173-w5-host-gate.sh'), f'W5 gate missing from {entry}'
for path in ['BUILD_ON_NAS.sh','.github/workflows/verify.yml','app/build.gradle']:
    assert release['gate'] in (r/path).read_text(),path
assert 'private static int contact(' in g and 'Intersecting cut contours' in g
assert 'ownership.own(encodedStage::discard)' in s and 'ownership.workerFinished(posted)' in s
assert 'for(ProjectLoadOwnership pending:pendingProjectLoads)pending.cancel()' in s
# Match the class-level method boundary; comments after the method are not API.
match=re.search(r'^    private void restoreLocal\(\)\s*\{.*?^    \}',s,re.M|re.S)
assert match is not None, 'restoreLocal method missing or unexpected formatting'
restore=match.group(0)
assert restore.index('schedulePhotoJob(()->')<restore.index('StartupPhotoLoader.hasSavedPhoto(directory)')
assert 'restoreOwnership.workerFinished(delivered)' in restore
assert 'synchronized void cancel()' in o and 'if(workerFinished)dispose()' in o
assert release['apk'] in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS W5 NAS/CI/Gradle source wiring, startup restore ordering and ownership hooks')

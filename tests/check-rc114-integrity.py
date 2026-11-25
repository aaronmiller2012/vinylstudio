#!/usr/bin/env python3
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
assert json.loads((r/'tests/release-info.json').read_text())['versionCode']>=406
js=(r/'app/src/main/java/com/halftonestudio/StudioLibraryJobs.java').read_text()
assert 'ArrayBlockingQueue<Runnable>(MAX_QUEUED)' in js
assert 'new ThreadPoolExecutor.AbortPolicy()' in js
assert 'work.shutdown()' in js and 'work.shutdownNow()' not in js
assert 'if(closed)return;' in js and 'value=job.call();' in js
assert 'CallerRunsPolicy' not in js.replace('CallerRunsPolicy is forbidden','')
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'VinylCrashReport.recordLibraryError' in a
for f in ['PHONE_COMMON.sh','PHONE_BUILD_AND_RECEIVE.sh','PHONE_RECEIVE_ONLY.sh','NAS_STAGE_BUILD.sh']:
    assert (r/'transfer'/f).is_file()
lib=(r/'transfer/PHONE_COMMON.sh').read_text()
for marker in ['sha256sum -c SOURCE_SHA256.txt','SOURCE VERIFIED on NAS','vinyl_receive_apk',
               'vinyl_notice','VINYL_LOG_DIR','VINYL_TMP_APK','VINYL_RETRIES=3',
               'SOURCE CACHE HIT','--delay-updates']:
    assert marker in lib, marker
nas=(r/'transfer/NAS_STAGE_BUILD.sh').read_text()
for marker in ['sha256sum -c -','mktemp -d','unzip -tq','ln -s','mv -Tf','trap rollback EXIT']:
    assert marker in nas, marker
build=(r/'BUILD_ON_NAS.sh').read_text()
assert 'STAGED_APK=' in build and 'mv -f -- "$STAGED_APK" "$DEST"' in build
print('PASS RC114 transactional scripts, checksum, manifest, queue and release policy')

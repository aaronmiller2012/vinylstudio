#!/usr/bin/env python3
"""Check release wiring and migration markers; behavior tests are separate JVM executions."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
info=json.loads((r/'tests/release-info.json').read_text())
assert info['versionCode']>=438 and info['versionName'].startswith(info['version'])
assert info['gate'] in (r/'BUILD_ON_NAS.sh').read_text()
ui=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'private final LatestRequestWorker paletteWorker' in ui
assert 'catch(OutOfMemoryError oom)' in ui
assert 'if(prepared!=null&&prepared!=source' in ui
assert 'paletteWorker.close()' in ui
assert 'OnBackInvokedDispatcher.PRIORITY_DEFAULT' in ui
assert 'setOnCancelListener(d->showUnifiedStyleDrawer())' in ui
assert 'void onSaveInstanceState(Bundle outState)' in ui
assert 'void onPause()' in ui
assert 'WindowInsets.Type.navigationBars()' in ui
assert 'enableOnBackInvokedCallback="false"' not in (r/'app/src/main/AndroidManifest.xml').read_text()
ci=(r/'.github/workflows/verify.yml').read_text()
assert info['gate'] in ci
for task in (':app:assembleDebug',':app:lintDebug',':app:assembleRelease'):
    assert task in ci,task
build=(r/'BUILD_ON_NAS.sh').read_text()
assert ':app:assembleRelease' in build
assert 'mapping/release/mapping.txt' in build
assert 'versionCode '+str(info['versionCode']) in (r/'app/build.gradle').read_text()
print('PASS RC161 UI navigation, lifecycle, palette worker, CI, R8 and release wiring')

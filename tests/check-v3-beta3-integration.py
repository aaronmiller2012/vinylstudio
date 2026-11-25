#!/usr/bin/env python3
"""Source integration and version gate, complementing executable Java tests."""
from pathlib import Path
import json
import re
from release_gate_graph import gate_reachable
r=Path(__file__).resolve().parents[1]
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=454
assert re.fullmatch(r'VinylHalftoneStudio-v' + re.escape(meta['versionName']) + r'-[A-Za-z0-9][A-Za-z0-9._-]*\.apk', meta['apk']), 'APK name must match active version, not a legacy Beta label'
source=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'StandardCutSafety.inspect(result' in source
# Beta 24 pre-save export intentionally replaced the legacy direct call.
# Android ACTION_CREATE_DOCUMENT creates the destination before the app writes it;
# never restore the old call just to satisfy a historical source-level assertion.
request=source.split('private void requestExport(){',1)[1].split('@Override protected void onActivityResult',1)[0]
assert 'beginPreparedStandardCutZip()' in request, 'Standard export must prepare before Save picker'
assert 'ACTION_CREATE_DOCUMENT' not in request, 'No user destination before preparation'
prepare=source.split('private PreparedStandardCutZip prepareStandardCutZip(',1)[1].split('private void writeProject(',1)[0]
assert prepare.index('stage.verify();') < prepare.index('return new PreparedStandardCutZip'), 'ZIP must verify before picker'
# Build 560: the selected Cricut mat is captured with the device and passed in, never read live.
assert 'prepareStandardCutZip(captured,base,device,mat,sourcePhoto,exportPhoto,baseOnly,projectSettingsSnapshot)' in source
assert 'final String mat=selectedCricutMat();' in source.split('private void beginPreparedStandardCutZip(){',1)[1].split('private void openPreparedStandardCutPicker',1)[0]
assert 'final String projectSettingsSnapshot=toJson(captured.settings).toString();' in source
picker=source.split('@Override protected void onActivityResult',1)[1].split('private void exportSilhouetteOnly',1)[0]
assert 'publishPreparedStandardCutZip(uri)' in picker, 'Selected URI must receive verified stage'
assert 'exportZip(uri,true,result)' not in source, 'Legacy unsafe direct export must remain retired'
assert 'ProjectSaveGate.begin()' in source
assert 'ProjectSourceSnapshot.capture(' in source
assert 'exportAlternate(uri,true,renderToken)' in source
assert 'Cut safety not fully verified' in source
assert 'exportSilhouetteOnly(uri,base,true)' in source
assert 'PhotoRevisionStore.commit(' in (r/'app/src/main/java/com/halftonestudio/ProjectPhotoStore.java').read_text()
assert 'PhotoRevisionStore.hasPhoto(dir)' in (r/'app/src/main/java/com/halftonestudio/StartupPhotoLoader.java').read_text()
assert 'inspectionContour(p)' in (r/'app/src/main/java/com/halftonestudio/StyleCutGeometryPreflight.java').read_text()
for relative in ('tests/V3Beta3ReliabilityTest.java','app/src/test/java/com/halftonestudio/V3Beta3UnitTest.java'):
 assert (r/relative).is_file()
for relative in ('BUILD_ON_NAS.sh','transfer/PHONE_BUILD_AND_RECEIVE.sh','transfer/PHONE_RECEIVE_ONLY.sh'):
 assert meta['apk'] in (r/relative).read_text(),relative
# The current style gate and inherited v3 host gate must both be reachable
# through real NAS and Play shell paths; they need not be nested in one script.
for entry in ('BUILD_ON_NAS.sh', 'BUILD_PLAY_BUNDLE_ON_NAS.sh'):
    assert gate_reachable(r, entry, meta['gate']), f'Current gate missing from {entry}'
    assert gate_reachable(r, entry, 'tests/run-v3-host-gate.sh'), f'Inherited v3 gate missing from {entry}'

assert ':app:testDebugUnitTest :app:lintDebug :app:assembleDebug' in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS v3 Beta3 code, release identity and transfer integration')

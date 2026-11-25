#!/usr/bin/env python3
"""Fail early if a future UI change opens SAF ahead of final ZIP preparation."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
request=s.split('private void requestExport(){',1)[1].split('@Override protected void onActivityResult',1)[0]
assert 'beginPreparedStandardCutZip()' in request
assert 'ACTION_CREATE_DOCUMENT' not in request, 'Save picker must open only after cut ZIP stage verifies'
prep=s.split('private PreparedStandardCutZip prepareStandardCutZip(',1)[1].split('private void writeProject(',1)[0]
assert 'stage.verify()' in prep and 'return new PreparedStandardCutZip' in prep
# JSONObject.toString(int) throws checked JSONException on Android. The export method
# declares IOException only; project JSON must use the nonthrowing serializer.
assert 'putZip(zip,"project-settings.json",projectSettingsSnapshot);' in prep, (
    'Project settings must be snapshotted on UI before background ZIP work'
)
assert 'final String projectSettingsSnapshot=toJson(captured.settings).toString();' in s
assert 'toJson(result.settings).toString(2)' not in prep
assert prep.index('stage.verify()') < prep.index('return new PreparedStandardCutZip')
chooser=s.split('private void openPreparedStandardCutPicker(',1)[1].split('private void publishPreparedStandardCutZip',1)[0]
assert 'preparedStandardCutZip' in chooser and 'ACTION_CREATE_DOCUMENT' in chooser
picker=s.split('@Override protected void onActivityResult',1)[1].split('private void exportSilhouetteOnly',1)[0]
assert 'publishPreparedStandardCutZip(uri)' in picker
assert 'discardPreparedStandardCutZip()' in picker
assert 'discardFailedStandardDocument(uri)' in s
assert 'DocumentsContract.deleteDocument' in s
assert 'if(baseOnly)' in prep
assert 'showStandardExportError' in s
print('PASS source flow: verified stage before picker, canceled review cleanup, base-only retention, persistent failures')

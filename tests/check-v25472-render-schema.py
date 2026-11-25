#!/usr/bin/env python3
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
src=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
schema=(root/'app/src/main/java/com/halftonestudio/ProjectSettingsSchema.java').read_text()
meta=json.loads((root/'tests/release-info.json').read_text())
assert meta['versionCode']>=406 and meta['gate'].startswith('tests/run-')
assert meta['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
assert meta['versionName'] in (root/'app/build.gradle').read_text()
assert 'RenderDraftPolicy.DEBOUNCE_MS' in src
# RC134 caches the same resampling formula instead of recomputing it for every drag.
draft=(root/'app/src/main/java/com/halftonestudio/RenderDraftSourceCache.java').read_text()
assert 'draftSourceCache.draft(input,w,h,vectorW,vectorH,photoRevision)' in src
assert 'input[yy*w+Math.min(w-1,(int)((x+.5)*w/dw))]' in draft
assert 'if(dw==w&&dh==h)return input;' in draft
assert 's.masterSpacingMm=Math.max(s.masterSpacingMm*1.45f' not in src
assert 's.lineSampleMm=RenderDraftPolicy.sampleStepMm' in src
assert 'if(!draft){preparedPixels=tonalInput;preparedSignature=tonalKey;}' in src
assert 'completedCounts=draft?null:LayerCutCounts.standard(out)' in src
assert 'if(layers!=null&&!draft)' in src
assert 'StyleLayerOperations.Counts[] operations=draft?null:' in src
assert 'projectSourceJson=new JSONObject(json.toString())' in src
assert 'new JSONObject(projectSourceJson.toString())' in src
assert 'previousLayers.optJSONObject(layerIndex++)' in src
assert 'ProjectSettingsMigration.migrate(json)' in src
assert 'Math.max(ProjectSettingsMigration.CURRENT_SCHEMA,projectSourceSchema)' in src
assert 'newerProjectSchemaWarning' in src
assert 'version<18' in schema and 'version<19' in schema and 'version<20' in schema and 'version<21' in schema and 'version<22' in schema
assert 'OLD_DEFAULT_MINIMUM_MM=.80f' in schema
assert len((root/'tests/golden/geometry-signatures.tsv').read_text().splitlines())==229
for v in (0,18,19,20,21,22,23,24,25,26):
    fixture=json.loads((root/'tests/golden'/f'project-schema-v{v}-synthetic.json').read_text())
    assert fixture['schema']==v and len(fixture['layers'])==4
    assert fixture['unknownPluginMetadata']['version']==v
    assert all('unknownLayerMetadata' in layer for layer in fixture['layers'])
assert 'VinylStyleSuite.restoredStyleId(id)' in (root/'tests/ProjectSettingsSchemaHostTest.java').read_text()
print('PASS RC106 code wiring, schema examples, preserved unknown JSON fields, release metadata')

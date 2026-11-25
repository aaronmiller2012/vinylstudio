from pathlib import Path
src=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'private volatile boolean alternatePreviewDraft=true' in src
assert 'alternatePreviewDraft=draft' in src
assert 'if(alternatePreviewDraft){toast(' in src
assert 'alternateReadyGeneration=-1;\n            alternatePreviewDraft=true;' in src
assert 'if(alternatePreviewDraft||renderToken<0' in src
assert 'if(latest==null||renderedSerial!=serial||standardPreviewDraft)' in src
assert 'previewSide=draft?600:1200' in src
assert 'specialtyBaseCache.get(baseKey)' in src and 'specialtyLitCache.get(fullKey)' in src
assert 'android.util.Log.d("VinylPerf", "specialty style="' in src
print('PASS: draft isolation, preview resolution, two-stage specialty cache and phase timing source contracts')

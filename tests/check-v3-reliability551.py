from pathlib import Path
p=Path('app/src/main/java/com/halftonestudio')
s=(p/'StudioActivity.java').read_text()
a=s.split('private void beginPhotoImport(Uri uri)',1)[1].split('private InputStream requireProjectStream',1)[0]
assert 'projectSession.beginLoad()' in a and a.count('projectSession.isCurrentLoad(token)')>=2
assert 'ownership.own(staged::close)' in a and 'ownership.workerFinished(posted)' in a
assert 'NativeOriginalPhoto.commit(getFilesDir(),staged)' in a
assert 'projectSession.markEdited()' in a
op=s.split('private void loadProject(Uri uri,String savedId,String savedName)',1)[1].split('private JSONObject toJson',1)[0]
assert 'OwnedSourceStage.capture(getCacheDir(),requireProjectStream(uri)' in op
assert 'projectLibrary.openArchive(savedId)' in op and 'try(InputStream source=external.open())' in op
assert 'getContentResolver().openInputStream(uri)' not in op
assert 'NativeOriginalPhoto.commit(getFilesDir(),nativeStage)' in op
assert 'projectLibrary=StudioProjectLibrary.shared(getFilesDir())' in s
assert 'projectLibrary.archive(entry.id)' not in s
assert 'projectLibrary.openArchive(savedId)' in op
assert 'projectSession.invalidateLoads();cancelStartupRestore();\n        Bitmap sample' in s
for f in ('BUILD_ON_NAS.sh','BUILD_PLAY_BUNDLE_ON_NAS.sh','.github/workflows/verify.yml','app/build.gradle'):
 assert 'tests/run-v3-reliability551-gate.sh' in Path(f).read_text(),f
print('PASS 551 import ownership, immutable provider bytes, worker-only library lookup, and release wiring')

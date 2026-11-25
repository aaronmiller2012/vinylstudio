from pathlib import Path
p=Path('app/src/main/java/com/halftonestudio');s=(p/'StudioActivity.java').read_text()
for start,end,arg in [('private void startNewStyleRender','private void startAlternateRender','adjustments'),('private void startAlternateRender','private void export','imageAdjustments')]:
    body=s.split(start,1)[1].split(end,1)[0]
    assert 'final int[] underlayPixels=pixels;' in body
    assert f'({arg},underlayPixels,underlayW,underlayH,underlayColor)' in body
    assert 'settings.copy(),pixels' not in body
assert 'portraitCache.get(sourceRevision,adjustedPixels,w,h)' in s
assert 'synchronized(this){portraitCache.clear();}' not in s and 'portraitCache.photoChanged(photoRevision)' in s
reuse=s.split('private boolean reuseRegistrationOnly()',1)[1].split('private void requestRegistrationRender()',1)[0]
assert 'previous.withRegistration' in reuse and 'tessellationResult=updated' in reuse
assert 'stippleVectorCache.rebind(updated)' in reuse and 'specialtyVectorCache.rebindTessellation(updated)' in reuse
assert 'currentUnderlay()!=null||underlayRequested()' in reuse
assert 'alternateReadyGeneration=++alternateGeneration' in reuse and 'discardPreparedSpecialtyCutZip();' in reuse
assert 'o.marks=manual' not in reuse
save=s.split('protected void onSaveInstanceState(Bundle outState)',1)[1].split('protected void onStop()',1)[0]
assert 'outState.putString(STATE_DRAFT' not in save and 'outState.putString(STATE_EDITOR_SNAPSHOT' not in save
assert 'persistLifecycleEditor();' in save
assert 'getString(STATE_EDITOR_SNAPSHOT)' in s and 'getString(STATE_DRAFT,startupSavedDraft)' in s # legacy migration
assert 'new BoundedEditHistory(40,2L*1024*1024)' in s
for f in ('BUILD_ON_NAS.sh','BUILD_PLAY_BUNDLE_ON_NAS.sh','.github/workflows/verify.yml','app/build.gradle'):
    assert 'tests/run-v3-stability550-gate.sh' in Path(f).read_text(),f
print('PASS 550 render ownership, mark revisions, bounded history and Binder-payload wiring')

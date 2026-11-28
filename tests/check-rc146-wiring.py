#!/usr/bin/env python3
"""RC146 integration contract: protect all cut/document producers and busy state."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
# RC146 originally required each export producer to call
# publishStagedCutZip(stage,uri) directly. V3 now deliberately completes and
# verifies a staged ZIP *before* opening the SAF picker; the selected URI is
# later handled by the shared prepared-specialty or prepared-standard publisher.
# Check that two-stage handoff instead of an obsolete exact call spelling.
for producer in ('exportNewStyle','exportAlternate','exportSilhouetteOnly'):
    assert 'private void '+producer+'(' in s, 'Missing cut export producer: '+producer
# M7 B5: the handoff now names the ZIP after the style (ExportFileNames, behavior-tested in
# M7DevicePerfTest b5) instead of fixed "art-style"/"specialty"/"solid-base" labels, so check
# directly that EACH producer hands its verified stage to the shared specialty publisher.
for producer in ('exportNewStyle','exportAlternate','exportSilhouetteOnly'):
    start=s.rindex('private void '+producer+'(');finish=s.find('\n    private void ',start+20)
    assert 'offerPreparedSpecialtyZip(verified,' in s[start:finish], 'Missing staged specialty handoff in '+producer
assert 'stage.verify();' in s[s.index('private void publishStagedCutZip'):s.index('private void exportNewStyle')]
specialty=s[s.index('private void publishPreparedSpecialtyZip'):s.index('private void requestAlternateExport')]
assert 'publishStagedCutZip(ready,uri)' in specialty
assert 'ready.close();' in specialty
assert 'discardFailedStandardDocument(uri)' in specialty
standard=s[s.index('private void publishPreparedStandardCutZip'):s.index('private void writeProject')]
assert 'publishStagedCutZip(ready.stage,uri)' in standard
assert 'ready.close();' in standard
assert 'discardFailedStandardDocument(uri)' in standard
assert 'stage.verifyPublishedCopy(saved)' in s[s.index('private void publishStagedCutZip'):s.index('private void exportNewStyle')]
assert 'else if(req==EXPORT)publishPreparedStandardCutZip(uri)' in s
assert 'else if(req==ALTERNATE_EXPORT)publishPreparedSpecialtyZip(uri)' in s
for name in ('exportAssemblyGuide','exportArtworkDocument'):
    needle='private void '+name+'('
    start=s.index(needle);finish=s.find('\n    private ',start+len(needle))
    assert 'publishStagedDocument(stage,uri,' in s[start:finish if finish>=0 else len(s)],name
assert 'stage.verify(type);' in s[s.index('private void publishStagedDocument'):s.index('private void publishStagedCutZip')]
assert 'if(!reuseRegistrationOnly())requestRender(true)' in s
assert 'catch(OutOfMemoryError oom)' in s[s.index('private void saveProjectToLibrary(String projectName,boolean'):s.index('private void openLibraryProject(')]
assert 'projectSession.failSave()' in s[s.index('private void saveProjectToLibrary(String projectName,boolean'):s.index('private void openLibraryProject(')]
assert 'RejectedExecutionException full' in s[s.index('private boolean scheduleDiskJob('):s.index('private final ExecutorService startupIo')]
assert 'new java.util.concurrent.ArrayBlockingQueue<Runnable>(32)' in (r/'app/src/main/java/com/halftonestudio/StudioRenderController.java').read_text()
print('PASS RC146: all cut/document export entry points stage before SAF publication; mark/save/queue paths wired')

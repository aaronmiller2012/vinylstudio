#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
a=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
l=(root/'app/src/main/java/com/halftonestudio/StudioLibraryJobs.java').read_text()
c=(root/'app/src/main/java/com/halftonestudio/StudioRenderController.java').read_text()
b=(root/'app/src/main/java/com/halftonestudio/StudioProjectBackup.java').read_text()
e=(root/'app/src/main/java/com/halftonestudio/NineLayerCraftPatternEngine.java').read_text()
# Truncating SAF output is now enforced by check-rc170-integration.py + LibraryDocumentTruncationTest.
assert 'LibraryDocumentStreams.document(' in a
for mark in ('outState.putString(STATE_PENDING_LIBRARY_EXPORT,pendingLibraryExport.save())',
             'pendingLibraryExport.restore(state.getString(STATE_PENDING_LIBRARY_EXPORT))',
             'private boolean postUi(Runnable callback)',
             'activityDestroyed=true;', 'preparedToneCache=new PreparedToneCache', 'final PreparedToneCache cachedTone=preparedToneCache;',
             'friendlyFailure(e,"Unable to open this project")',
             'private static int paletteIndex(', 'private static float finiteRange('):
    assert mark in a, mark
assert 'if (closed) return java.util.concurrent.CompletableFuture.completedFuture(null)' in c
assert 'StudioProjectBackup.preflight(library)' in l
assert 'Set<String> known=new HashSet<>()' in b
assert 'library.newRestoreTemp()' in b
assert 'if(!known.contains(hash))' in b
assert 'float x0=left,x1=right;' in e
assert 'row=(o.pattern==FISHNET?-1:0)' in e
print('PASS inherited RC169 Android SAF, activity guard, cache, backup, and textile safety contracts (RC170 adapters)')

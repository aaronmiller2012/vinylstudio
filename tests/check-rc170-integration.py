#!/usr/bin/env python3
"""Secondary wiring assertions; actual behavior is exercised by RC170 Java tests."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(r/'app/src/main/java/com/halftonestudio/NineLayerCraftPatternEngine.java').read_text()
b=(r/'app/src/main/java/com/halftonestudio/StudioProjectBackup.java').read_text()
l=(r/'app/src/main/java/com/halftonestudio/StudioProjectLibrary.java').read_text()
# RC170 requirement: user-selected library documents are replaced ("wt"), never
# appended to. LibraryDocumentTruncationTest proves the behavior for every job;
# this only proves the Activity routes every OutputTarget job through that path.
# Operations are discovered from StudioLibraryJobs, so adding one cannot bypass it.
import re
jobs=(r/'app/src/main/java/com/halftonestudio/StudioLibraryJobs.java').read_text()
outputs=[m for m in re.findall(r'^\s*(?:[\w<>\[\]]+\s+)+(\w+)\s*\([^)]*\bOutputTarget\b',jobs,re.M)
         if m not in ('interface','open')]
assert {'exportProject','exportDamagedProject','backup'} <= set(outputs), outputs
for name in outputs:
    for call in re.finditer(r'libraryJobs\.'+name+r'\(',a):
        statement=a[call.start():a.index(';',call.start())]
        assert 'libraryDocument(' in statement, 'unguarded library output: '+name
helper=re.search(r'OutputTarget\s+libraryDocument\(Uri\s+\w+\)\{(.*?)\n    \}',a,re.S)
assert helper, 'libraryDocument helper missing'
# The opener must forward the mode chosen by LibraryDocumentStreams ("wt") unchanged;
# a hard-coded mode such as "w" would silently stop truncating on some providers.
body=re.sub(r'\s+','',helper.group(1))
assert re.fullmatch(r'returnLibraryDocumentStreams\.document\((\w+)->getContentResolver\(\)\.openOutputStream\(\w+,\1\)\);',body), \
    'libraryDocument must pass the truncate mode straight to openOutputStream: '+body
assert 'openOutputStream(' not in a.replace(helper.group(0),'').split('private void exportLibraryArchive')[1].split('private void importLibraryBackup')[0]

assert 'pendingLibraryExport.restore(' in a and 'pendingLibraryExport.save()' in a
assert 'lifecycleUi.close()' in a and 'lifecycleUi.dispatch(' in a
assert 'FailureText.describe(error,fallback)' in a
assert 'LoadValueConstraints.finiteRange(value,min,max,fallback)' in a
assert 'if(!cell.perimeter)assignmentCounts[cell.layer]++' in e
assert 'known.add(library.archiveDigest(old.id))' in b
assert 'persistArchiveHash(new File(pending,"card.properties")' in l
assert (r/'docs/engineering/RC170-INTEGRITY-TONE-NOTES.md').is_file()
print('PASS RC170 integration wiring checks; behavior tested by Java executables')

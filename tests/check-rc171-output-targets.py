#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
prof=(root/'app/src/main/java/com/halftonestudio/OutputDeviceProfile.java').read_text()
assert s.count('writeOutputHandoff(zip,targetForThisExport);')==3 and s.count('writeOutputHandoff(zip,target);')==1, 'Not every cut export path has target metadata'
assert 'requestArtworkDocument(true);return;' in s, 'Paper printer target has no PDF export route'
assert 'getInt("outputDeviceId",OutputDeviceProfile.CRICUT)' in s, 'Missing fallback preference'
assert 'setSingleChoiceItems(options,outputDeviceId' in s, 'No destination selector in UI'
assert s.count('final int targetForThisExport=outputDeviceId;')==3 and s.count('final int device=outputDeviceId;')==1, 'Export target not snapshotted for background work'
assert 'No output target changes your cut paths' in s, 'No unchanged-geometry disclosure'
assert 'Basic Edition does not import SVG' in prof, 'Silhouette version guard missing'
assert 'not machine-readable FCM' in prof, 'Brother conversion limitation missing'
assert 'direct' not in prof.lower() or 'no device commands' in prof.lower(), 'Direct machine control may have been claimed'
assert 'java.io' not in prof, 'Keep platform-independent profile module'
print('PASS RC171 UI wiring, guard rails, and four ZIP handoff metadata branches')

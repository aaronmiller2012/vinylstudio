#!/usr/bin/env python3
"""V3 Beta2 targeted source wiring checks, not a replacement for host Java tests."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=453 and meta['apk'].endswith('.apk')
main=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'CutSafetyReviewPolicy.assess(geometryWarnings,500)' in main
# Re-export after review passes the REVIEWED cut-ready geometry (ADR-0002); reuse is
# behaviour-tested by tests/V3PreviewExportParityTest.java.
assert 'exportNewStyle(uri,true,token,cut)' in main
assert 'cutReview.requiresConfirmation()' in main
assert 'cutReview.dangerous()' in main
assert 'cut-safety-review.csv' in main
reader=(r/'app/src/main/java/com/halftonestudio/ProjectArchiveReader.java').read_text()
assert 'EntryPolicy policy=new EntryPolicy();' in reader
assert 'Multiple photo entries' in reader and 'Multiple original entries' in reader
library=(r/'app/src/main/java/com/halftonestudio/StudioProjectLibrary.java').read_text()
assert library.count('ProjectArchiveReader.EntryPolicy policy=new ProjectArchiveReader.EntryPolicy()')==2
assert (r/'tests/V3Beta2SafetyTest.java').exists()
assert meta['apk'] in (r/'transfer/PHONE_RECEIVE_ONLY.sh').read_text()
print('PASS v3 Beta2 export acknowledgement, common archive roles and release/receive wiring')

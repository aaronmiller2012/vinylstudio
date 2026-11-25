#!/usr/bin/env python3
"""RC84 source wiring checks supplement behavioral Java tests."""
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
j=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
g=(r/'app/src/main/java/com/halftonestudio/VinylStyleSuite.java').read_text()
name=(r/'tests/release-info.json'); release=json.loads(name.read_text())
assert release['versionCode']>=362
assert tuple(map(int, release['version'].split('.'))) >= (2,54,50)
assert 'PhotoStyleDefaults.forNewStyle(id' in j
assert 'selectionSuggestion(' in j and 'selectionPhotoStats()' in j
assert j.count('        addPhotoAutoAction(')==0, 'No redundant best settings action should be shown'
assert 'drawGestureSnapshot(cv);return;' in j
assert 'releaseGestureSnapshot();invalidate();' in j
assert 'Math.max(min,Math.max(.5f' in g
assert '--build-cache' in (r/'BUILD_ON_NAS.sh').read_text()
assert '--configuration-cache' in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS RC84 integration wiring and release identity')

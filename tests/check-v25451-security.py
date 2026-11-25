#!/usr/bin/env python3
"""RC85 wiring checks supplement JVM behavioral tests, not a substitute for Android lint."""
from pathlib import Path
import json,re
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
d=(r/'app/src/main/java/com/halftonestudio/DocumentAccess.java').read_text()
i=json.loads((r/'tests/release-info.json').read_text())
assert i['versionCode']>=363 and tuple(map(int,i['version'].split('.'))) >= (2,54,51)
assert i['gate'].startswith('tests/run-') and i['gate'].endswith('-gate.sh')
assert i['apk'] in (r/'BUILD_ON_NAS.sh').read_text()
assert i['versionName'] in (r/'app/build.gradle').read_text()
assert 'private volatile boolean includeSourcePhotoInCutZip=false' in a
assert 'final boolean includeSource=includeSourcePhotoInCutZip' in a
assert a.count('if(includeSource)')==3
assert a.count('putJpeg(zip,"source-photo.jpg"')==3
assert 'includeSourcePhotoInCutZip=false; // never import sharing consent' in a
assert 'DocumentAccess.releaseUnusedProjectGrants(getContentResolver(),updated);' in a
assert 'getPersistedUriPermissions()' in d and 'releasePersistableUriPermission' in d
assert 'androidx.exifinterface.media.ExifInterface' in a
assert 'rnd.nextFloat()' not in (r/'app/src/main/java/com/halftonestudio/LinocutEngine.java').read_text()
assert 'ThumbnailHeader.validate(thumbnail);' in (r/'app/src/main/java/com/halftonestudio/StudioProjectLibrary.java').read_text()
workflow=(r/'.github/workflows/verify.yml').read_text()
assert 'permissions:\n  contents: read' in workflow and ':app:lintDebug' in workflow
assert all(len(s)==40 for s in re.findall(r'uses: [^@ ]+@([a-f0-9]{40})',workflow))
assert not re.search(r'uses: [^\s]+@v\d',workflow)
assert (r/'.gitignore').exists() and 'dataExtractionRules="@xml/data_extraction_rules"' in (r/'app/src/main/AndroidManifest.xml').read_text()
assert 'distributionSha256Sum=' in (r/'gradle/wrapper/gradle-wrapper.properties').read_text()
assert 'EXPECTED=' in (r/'gradlew').read_text()
print('PASS RC85 privacy, compatibility, SAF grants, build integrity and release metadata checks')

from release_version import at_least
from pathlib import Path
import json
r=Path('.')
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
t=(r/'app/src/main/java/com/halftonestudio/StudioProjectArchiveWriter.java').read_text()
i=json.loads((r/'tests/release-info.json').read_text())
assert i['versionCode']>=360 and at_least(i['versionName'], '2.54.0')
assert 'RecentProjectUris.remember(uri.toString()' in s
assert 'if(persistable)rememberProject(uri)' in s
assert 'ExifOrientationTransform.matrix(orientation)' in s
assert 'PhotoImportSizing.sampleSize(bounds.outWidth,bounds.outHeight,bound)' in s
assert 'StudioProjectLibrary.verifyProjectArchive(stage)' in s
assert 'File.createTempFile("vinyl-project-"' in s
assert 'openOutputStream(uri,"wt")' in s
assert 'ProjectArchiveReader.MAX_SETTINGS_ENTRY' in t
assert 'ProjectArchiveReader.MAX_IMAGE_ENTRY' in t
for script in ['run-host-tests.sh','run-release-smoke.sh']:
    assert '-sourcepath app/src/main/java' in (r/'tests'/script).read_text()
print('PASS RC82 source wiring and version checks')

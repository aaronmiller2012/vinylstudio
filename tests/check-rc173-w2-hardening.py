#!/usr/bin/env python3
from pathlib import Path
import re,subprocess,xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
g=(r/'app/build.gradle').read_text()
assert 'validateReleasePrivacy' in g and "'bundleRelease', 'assembleRelease', 'lintRelease'" in g
assert 'task.dependsOn(requireReleasePrivacy)' in g and "'preReleaseBuild'" in g
assert 'VINYL_PRIVACY_URL' in g and "startsWith('https://')" in g
assert 'tests/'+__import__('json').loads((r/'tests/release-info.json').read_text())['gate'].split('/')[-1] in g
ci=(r/'.github/workflows/verify.yml').read_text()
assert __import__('json').loads((r/'tests/release-info.json').read_text())['gate'] in ci and 'run-rc172-dxf-gate.sh' not in ci
for stage in (':app:testReleaseUnitTest',':app:lintRelease',':app:assembleRelease',':app:bundleRelease'):
    assert stage in ci,stage
info=__import__('json').loads((r/'tests/release-info.json').read_text())
assert ('v3-code%d-r8-mapping'%info['versionCode']) in ci or 'rc173-code451-r8-mapping' in ci
readme=(r/'README.md').read_text()
assert ('versionCode %d'%info['versionCode']) in readme
# A historical README section may mention an old build; require the current banner to match.
assert re.search(r'\*\*Current build:\*\*[^\n]*\bversionCode %d\b'%info['versionCode'],readme)
assert info['apk'] in readme.splitlines()[2]
wrapper=(r/'gradlew').read_text()
assert 'JAVA_HOME' in wrapper and 'java.specification.version' in wrapper and 'JAVA_MAJOR<17' in wrapper
assert '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046' in (r/'scripts/ensure-gradle-wrapper.sh').read_text()
play=(r/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()
assert play.index('mv -f -- "$MAP_STAGE" "$MAP_DEST"')<play.index('mv -f -- "$STAGED" "$DEST"')
assert 'com.halftonestudio' in g
manifest=ET.parse(r/'app/src/main/AndroidManifest.xml').getroot()
changes=manifest.find('application').find('activity').get('{http://schemas.android.com/apk/res/android}configChanges')
assert 'smallestScreenSize' in changes and 'screenLayout' in changes
assert 'fontScale' not in changes and 'density' not in changes and 'uiMode' not in changes
main=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'STATE_EDITOR_SNAPSHOT' in main and 'photoPreviewLimit(1800)' in main
assert 'MediaStore.ACTION_PICK_IMAGES' in main and 'Intent.ACTION_OPEN_DOCUMENT' in main
assert 'Not enough free storage' in (r/'app/src/main/java/com/halftonestudio/NativeOriginalPhoto.java').read_text()
assert 'No web browser available' in (r/'app/src/main/res/values/strings.xml').read_text()
assert (r/'app/src/main/java/com/halftonestudio/PhotoMemoryPolicy.java').is_file()
for p in ('gradlew','BUILD_ON_NAS.sh','BUILD_PLAY_BUNDLE_ON_NAS.sh','tests/run-rc173-w2-host-gate.sh'):
    subprocess.run(['bash','-n',str(r/p)],check=True)
print('PASS W2 release privacy, CI, JVM, identity, config lifecycle, insets, photo and atomic publication source gates')

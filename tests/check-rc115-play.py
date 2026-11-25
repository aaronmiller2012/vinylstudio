#!/usr/bin/env python3
"""Static release preflight. This does not substitute for Android SDK build / Play review."""
from pathlib import Path
import json, re, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
gradle=(root/'app/build.gradle').read_text()
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
build=(root/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()
info=json.loads((root/'tests/release-info.json').read_text())
assert re.search(r'compileSdk\s+36\b',gradle), 'compileSdk must be 36'
assert re.search(r'targetSdk\s+36\b',gradle), 'targetSdk must be 36'
assert re.search(r'minSdk\s+26\b',gradle), 'minSdk changed unexpectedly'
assert re.search(r'versionCode\s+' + str(info['versionCode']) + r'\b', gradle), 'Gradle versionCode differs from release-info.json'
assert info['versionCode']>=393 and tuple(int(x) for x in info['version'].split('.')[:2]) >= (2,54), 'Release metadata unexpectedly old'
assert info['versionName'] in gradle, 'Gradle versionName differs from release-info.json'
assert 'signingConfigs.production' in gradle
assert 'VINYL_RELEASE_KEYSTORE' in gradle and 'VINYL_RELEASE_KEY_PASSWORD' in gradle
assert 'bundleRelease' in build and 'jarsigner -verify' in build
assert "tests/run-rc125-palette-offset-gate.sh" in build
assert "tests/check-rc115-play.py" in build
assert 'sha256sum' in build and 'mktemp' in build
assert not any(line.lstrip().startswith('apksigner ') for line in build.splitlines()), 'AAB must not use APK signing tool'
assert 'startForegroundService' not in (root/'app/src/main/java/com/halftonestudio/StudioLibraryJobs.java').read_text()
ANDROID='{http://schemas.android.com/apk/res/android}'
assert not manifest.findall('uses-permission'), 'Unexpected manifest permission; review before Play release'
app=manifest.find('application')
assert app is not None and app.attrib.get(ANDROID+'allowBackup')=='false'
assert app.attrib.get(ANDROID+'usesCleartextTraffic')=='false'
for component in ('service','receiver'):
    assert not app.findall(component), f'Unexpected {component}: requires exported/permission audit'
# Build 553 audit: exactly one provider, the private read-only cut-sheet share provider.
# It must stay non-exported (access only through per-share URI grants) and read-only.
providers=app.findall('provider')
assert len(providers)<=1, 'Unexpected provider: requires exported/permission audit'
for provider in providers:
    assert provider.attrib.get(ANDROID+'name')=='.SharedExportProvider', 'Unexpected provider: requires audit'
    assert provider.attrib.get(ANDROID+'exported')=='false', 'Share provider must not be exported'
    assert provider.attrib.get(ANDROID+'grantUriPermissions')=='true'
    assert provider.attrib.get(ANDROID+'authorities')=='com.halftonestudio.sharedexports'
    assert not any(k.endswith('Permission') for k in provider.attrib), 'Share provider must not declare permission bypasses'
    src=(root/'app/src/main/java/com/halftonestudio/SharedExportProvider.java').read_text()
    assert 'mode.equals("r")' in src and 'UnsupportedOperationException("read-only")' in src, 'Share provider must be read-only'
activities=app.findall('activity')
assert len(activities)==1 and activities[0].attrib.get(ANDROID+'exported')=='true'
assert (root/'app/src/main/res/xml/data_extraction_rules.xml').is_file()
assert not list(root.rglob('*.jks')), 'Do not include keystores in source package'
assert not list(root.rglob('*.keystore')), 'Do not include keystores in source package'
print('PASS RC115 Play preflight: target 36, manifest minimal, fail-closed signing, no bundled keys')

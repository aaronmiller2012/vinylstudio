#!/usr/bin/env python3
"""Static preflight only; real Java/DXF tests follow in run-rc173-play-readiness-gate.sh."""
from pathlib import Path
import json, re, xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
app=(r/'app/build.gradle').read_text()
main=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
privacy_xml=(r/'app/src/main/res/values/strings.xml')
manifest=ET.parse(r/'app/src/main/AndroidManifest.xml').getroot()
resource=ET.parse(privacy_xml)
strings={s.get('name'):s.text for s in resource.findall('string')}
play=(r/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()
debug=(r/'BUILD_ON_NAS.sh').read_text()
info=json.loads((r/'tests/release-info.json').read_text())
assert info['versionCode']>=450 and (info['versionName'].startswith('2.55.') or info['versionName'].startswith('3.'))
assert 'rootProject.name = \'VinylStudio\'' in (r/'settings.gradle').read_text()
assert re.search(r"applicationId 'com\.halftonestudio'",app), 'Do not accidentally change Play identity'
assert 'resValue \'string\', \'privacy_policy_url\', vinylPrivacyUrl' in app
assert 'privacy_policy_view_online' in strings and 'privacy_about_text' in strings
assert 'showPrivacyAndAbout()' in main and 'getString(R.string.privacy_policy_url)' in main
assert not manifest.findall('uses-permission')
assert manifest.find('application').attrib['{http://schemas.android.com/apk/res/android}label']=='@string/app_name'
assert strings['app_name']=='VinylStudio'
assert 'testImplementation "junit:junit:4.13.2"' in app
assert (r/'app/src/test/java/com/halftonestudio/CutExporterUnitTest.java').is_file()
assert (r/'app/src/test/java/com/halftonestudio/ProjectLoadUnitTest.java').is_file()
assert '-keep public class com.halftonestudio.StudioActivity { *; }' not in (r/'app/proguard-rules.pro').read_text()
assert '-assumenosideeffects class android.util.Log' in (r/'app/proguard-rules.pro').read_text()
assert 'scripts/check-public-privacy-policy.sh' in play
assert 'scripts/ensure-gradle-wrapper.sh' in play and 'scripts/ensure-gradle-wrapper.sh' in debug
assert info['gate'] in play or 'tests/run-rc173-w2-host-gate.sh' in play
for task in (':app:testReleaseUnitTest',':app:lintRelease',':app:assembleRelease',':app:bundleRelease'):
    assert task in play, f'Missing Play release quality gate {task}'
assert 'lintRelease' in debug
assert '2.54.99-Play-RC133' not in play
assert 'VINYL_PRIVACY_URL' in play and 'VINYL_PRIVACY_CONTACT' in play
assert f"V3-code{info['versionCode']}" in debug if info['versionName'].startswith('3.') else 'RC173-code451' in debug
for f in ('PHONE_BUILD_AND_RECEIVE.sh','PHONE_RECEIVE_ONLY.sh','VERIFY_PACKAGE.sh','README-FIRST.txt','NAS_STAGE_BUILD.sh'):
    s=(r/'transfer'/f).read_text()
    assert 'RC168' not in s and 'v2.55.33' not in s, 'Stale RC168 release configuration: '+f
assert (r/'scripts/ensure-gradle-wrapper.sh').is_file()
assert '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046' in (r/'scripts/ensure-gradle-wrapper.sh').read_text()
assert (r/'legal/privacy-policy-template.html').is_file()
print('PASS RC173 source metadata / privacy-link plumbing / R8 / Play gates / transfer isolation')

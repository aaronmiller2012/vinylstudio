#!/usr/bin/env python3
"""Static security checks for the shipped source. No claim of device penetration testing."""
from pathlib import Path
import re, json, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
app=root/'app/src/main/java/com/halftonestudio'
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
assert not manifest.findall('uses-permission'), 'Unexpected Android permission added'
application=manifest.find('application')
assert application is not None
assert application.get(ns+'allowBackup')=='false', 'Android backup must remain off'
assert application.get(ns+'usesCleartextTraffic')=='false', 'Cleartext traffic unexpectedly enabled'
components=[]
for tag in ('activity','service','receiver','provider'):
    components.extend(application.findall(tag))
assert len(components)==1 and components[0].get(ns+'name')=='.StudioActivity', 'Unexpected exposed component'
assert components[0].get(ns+'exported')=='true', 'Launcher must remain exported'
engine=(app/'BanknoteEngravingEngine.java').read_text()
policy=(app/'NewStyleVariantPolicy.java').read_text()
ui=(app/'StudioActivity.java').read_text()
assert 'RETIRED_GUILLOCHE = 6' in engine
assert 'private static void decorate(' not in engine and 'addGuillocheBand' not in engine
assert 'variant == RETIRED_GUILLOCHE' in engine and 'newStyleVariant=NewStyleVariantPolicy.next' in ui
assert 'savedVariant==BanknoteEngravingEngine.RETIRED_GUILLOCHE' in policy
assert 'decorArgb' not in engine and 'guillocheRadiusMm' not in engine
assert 'variant==BanknoteEngravingEngine.GUILLOCHE' not in (app/'NewStyleColorSlots.java').read_text()
# These are intentionally narrow negative checks, with dedicated behavioral tests alongside.
java='\n'.join(p.read_text(errors='replace') for p in app.glob('*.java'))
for pattern in (r'Runtime\.getRuntime\(\)\.exec\(',r'new\s+ProcessBuilder\s*\(',r'\.setJavaScriptEnabled\(\s*true\s*\)',
                r'ALLOW_ALL_HOSTNAME_VERIFIER',r'android\.permission\.MANAGE_EXTERNAL_STORAGE'):
    assert not re.search(pattern,java), 'Dangerous API pattern: '+pattern
archive=(app/'ProjectArchiveReader.java').read_text()
for marker in ('MAX_ENTRIES','MAX_UNCOMPRESSED','MAX_IMAGE_ENTRY','MAX_SETTINGS_ENTRY','MAX_SOURCE_ENTRY','name.contains("..")','Duplicate project archive entry'):
    assert marker in archive, 'Project import guard missing: '+marker
wrapper=(root/'gradle/wrapper/gradle-wrapper.properties').read_text()
boot=(root/'gradlew').read_text()
assert 'distributionSha256Sum=' in wrapper and 'EXPECTED=' in boot and 'sha256sum' in boot
info=json.loads((root/'tests/release-info.json').read_text())
assert info['versionCode']>=406 and info['gate'].startswith('tests/run-')
assert info['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
assert info['versionName'] in (root/'app/build.gradle').read_text()
assert len((root/'tests/golden/geometry-signatures.tsv').read_text().splitlines())==229
print('PASS RC109 static security baseline: manifest, exports, archive limits, pinned wrapper, retired style')

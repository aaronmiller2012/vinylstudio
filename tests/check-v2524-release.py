from pathlib import Path
import json,re
p=Path(__file__).resolve().parents[1]
m=json.loads((p/'tests/release-info.json').read_text())
g=(p/'app/build.gradle').read_text()
assert int(re.search(r'versionCode\s+(\d+)',g).group(1))==m['versionCode']==303
assert m['versionName'] in g and m['apk']=="VinylHalftoneStudio-v2.52.4-AllVector-RC25.apk"
assert "commandLine 'bash', 'tests/run-v2524-gate.sh'" in g
assert 'bash tests/run-v2524-gate.sh' in (p/'BUILD_ON_NAS.sh').read_text()
assert m['apk'] in (p/'BUILD_ON_NAS.sh').read_text()
print('PASS v2.52.4 release info, Gradle gate, APK naming')

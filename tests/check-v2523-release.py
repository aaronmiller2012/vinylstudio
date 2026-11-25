from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1]
j=(root/'app/src/main/java/com/halftonestudio')
a=(j/'StudioActivity.java').read_text();v=(j/'VinylEngine.java').read_text();l=(j/'LineArtEngine.java').read_text();p=(j/'SmartHalftonePresets.java').read_text()
g=(root/'app/build.gradle').read_text()
info=json.loads((root/'tests/release-info.json').read_text())
assert int(re.search(r'versionCode\s+(\d+)',g).group(1))==info['versionCode']
assert re.search(r'versionName\s+[\'\"]([^\'\"]+)',g).group(1)==info['versionName']
assert f"commandLine 'bash', 'tests/run-v2524-gate.sh'" in g, 'Gradle runs old hostRegression gate'
assert 'bash tests/run-v2524-gate.sh' in (root/'BUILD_ON_NAS.sh').read_text()
assert not (j/'PortraitCarrierScreen.java').exists(), 'disliked mode still shipped'
for source in (a,v,l):
    assert 'portraitCarrierScreen' not in source and 'PortraitCarrierScreen' not in source
for value in ('classicCmykLines','ClassicCmykLines.apply(settings)'):
    assert value in a
assert 'json.optBoolean("classicCmykLines",false)' in a, 'previous project defaults changed unexpectedly'
assert 'out.put("classicCmykLines",s.classicCmykLines)' in a, 'classic toggle not serialized'
assert 's.classicCmykLines=classicCmykLines' in v, 'copy loses setting'
assert 'ClassicLineArtEngine.generate' in l
assert 'ClassicCmykLines.apply(settings)' in p
assert 'versionCode 303' in g
print('PASS preserved classic line release metadata, source removal, migration, Gradle gate and UI wiring')

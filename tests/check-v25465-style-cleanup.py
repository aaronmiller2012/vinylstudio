#!/usr/bin/env python3
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/com/halftonestudio'
activity=(java/'StudioActivity.java').read_text()
suite=(java/'VinylStyleSuite.java').read_text()
metadata=json.loads((root/'tests/release-info.json').read_text())
assert metadata['versionCode']>=377 and metadata['gate'].startswith('tests/run-')
assert metadata['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
assert metadata['versionName'] in (root/'app/build.gradle').read_text()
assert not list(java.glob('Typography*.java')),'Retired engine/font/detector Java files remain'
assert 'play-services-mlkit-face-detection' not in (root/'app/build.gradle').read_text()
assert 'com.google.mlkit.vision.DEPENDENCIES' not in (root/'app/src/main/AndroidManifest.xml').read_text()
assert 'Typography Portrait · solid base' not in activity
assert 'activateNewStyle(VinylStyleSuite.TYPOGRAPHY_PORTRAIT)' not in activity
assert 'TypographyPortraitEngine' not in suite
assert 'TYPOGRAPHY_PORTRAIT' not in suite
assert '"Typography Portrait"' not in suite
assert 'RETIRE' in suite and 'restoredStyleId' in suite
assert 'newStyleId=VinylStyleSuite.restoredStyleId(storedStyleId)' in activity
assert 'out.put("typographyBaseColor"' not in activity
assert 'config.typographyFont' not in activity
assert 'if(newStyleId==VinylStyleSuite.RETIRED_STYLE_ID)' not in activity
assert 'VinylStyleSuite.isRetiredStyleId(storedStyleId)' in activity
assert not list(root.glob('*TYPOGRAPHY*.md'))
print('PASS RC99: typography code, UI, dependencies, and nine-sheet backend removed; legacy restore guarded')

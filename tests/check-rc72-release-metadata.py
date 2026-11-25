#!/usr/bin/env python3
"""Fail early when RC72 build provenance / inherited gates drift."""
import json,re
from pathlib import Path
r=Path(__file__).resolve().parents[1]
i=json.loads((r/'tests/release-info.json').read_text())
g=(r/'app/build.gradle').read_text()
b=(r/'BUILD_ON_NAS.sh').read_text()
expected=({'version': '2.54.52', 'versionCode': 364, 'versionName': '2.54.52-stability-rc86', 'apk': 'VinylHalftoneStudio-v2.54.52-Stability-RC86.apk', 'gate': 'tests/run-v25452-stability-gate.sh'} if i.get('versionCode')==364 else
 {'version': '2.54.51', 'versionCode': 363, 'versionName': '2.54.51-security-rc85', 'apk': 'VinylHalftoneStudio-v2.54.51-Security-RC85.apk', 'gate': 'tests/run-v25451-security-gate.sh'} if i.get('versionCode')==363 else
 {'version': '2.54.50', 'versionCode': 362, 'versionName': '2.54.50-performance-rc84', 'apk': 'VinylHalftoneStudio-v2.54.50-Performance-RC84.apk', 'gate': 'tests/run-v25450-performance-gate.sh'} if i.get('versionCode')==362 else
 {'version': '2.54.49', 'versionCode': 361, 'versionName': '2.54.49-guide-rc83', 'apk': 'VinylHalftoneStudio-v2.54.49-Guide-RC83.apk', 'gate': 'tests/run-v25449-guide-gate.sh'} if i.get('versionCode')==361 else
 {'version': '2.54.48', 'versionCode': 360, 'versionName': '2.54.48-integrity-rc82', 'apk': 'VinylHalftoneStudio-v2.54.48-Integrity-RC82.apk', 'gate': 'tests/run-v25448-integrity-gate.sh'} if i.get('versionCode')==360 else
 {'version': '2.54.47', 'versionCode': 359, 'versionName': '2.54.47-palette-harmony-rc81', 'apk': 'VinylHalftoneStudio-v2.54.47-PaletteHarmony-RC81.apk', 'gate': 'tests/run-v25447-palette-harmony-gate.sh'} if i.get('versionCode')==359 else
 {'version':'2.54.46','versionCode':358,
 'versionName':'2.54.46-glass-cutfix-rc80',
 'apk':'VinylHalftoneStudio-v2.54.46-GlassCutFix-RC80.apk',
 'gate':'tests/run-v25446-glass-cutfix-gate.sh'} if i.get('versionCode')==358 else
 {'version':'2.54.45','versionCode':357,
 'versionName':'2.54.45-glass-portrait-rc79',
 'apk':'VinylHalftoneStudio-v2.54.45-GlassPortrait-RC79.apk',
 'gate':'tests/run-v25445-glass-portrait-gate.sh'} if i.get('versionCode')==357 else
 {'version':'2.54.44','versionCode':356,
 'versionName':'2.54.44-reliability-rc78',
 'apk':'VinylHalftoneStudio-v2.54.44-Reliability-RC78.apk',
 'gate':'tests/run-v25444-reliability-gate.sh'} if i.get('versionCode')==356 else
 {'version':'2.54.43','versionCode':355,
 'versionName':'2.54.43-cut-sheet-rc77',
 'apk':'VinylHalftoneStudio-v2.54.43-CutSheet-RC77.apk',
 'gate':'tests/run-v25443-cut-sheet-gate.sh'} if i.get('versionCode')==355 else
 {'version':'2.54.42','versionCode':354,
 'versionName':'2.54.42-palette-tap-rc76',
 'apk':'VinylHalftoneStudio-v2.54.42-PaletteTap-RC76.apk',
 'gate':'tests/run-v25442-palette-tap-gate.sh'} if i.get('versionCode')==354 else
 {'version':'2.54.41','versionCode':353,
 'versionName':'2.54.41-visible-controls-rc75',
 'apk':'VinylHalftoneStudio-v2.54.41-VisibleControls-RC75-LayersCrashFix.apk',
 'gate':'tests/run-v25441-visible-controls-gate.sh'} if i.get('versionCode')==353 else
 {'version':'2.54.40','versionCode':352,
 'versionName':'2.54.40-physical-layers-rc74',
 'apk':'VinylHalftoneStudio-v2.54.40-PhysicalLayers-RC74.apk',
 'gate':'tests/run-v25440-physical-layers-gate.sh'} if i.get('versionCode')==352 else
 {'version':'2.54.38','versionCode':350,
 'versionName':'2.54.38-automatic-silhouette-rc72',
 'apk':'VinylHalftoneStudio-v2.54.38-AutoSilhouette-RC72-CompileFix.apk',
 'gate':'tests/run-v25438-auto-silhouette-gate.sh'} if i.get('versionCode')==350 else
 {'version':'2.54.39','versionCode':351,
 'versionName':'2.54.39-silhouette-l1-rc73',
 'apk':'VinylHalftoneStudio-v2.54.39-SilhouetteL1-RC73.apk',
 'gate':'tests/run-v25439-silhouette-l1-gate.sh'})
assert all(i.get(k)==v for k,v in expected.items()),f'release-info mismatch: {i}'
assert re.search(r'\bversionCode\s+'+str(i['versionCode'])+r'\b',g)
assert re.search(r"\bversionName\s+['\"]"+re.escape(i['versionName'])+r"['\"]",g)
assert i['gate'] in b
assert i['apk'] in b
assert (r/i['gate']).is_file()
# Historical checks need only name gates that existed when they shipped.
# RC82's own gate verifies current release metadata and delegates to this suite.
if i['versionCode']<=359:
    for t in ('check-v25419-cleanup.py','check-v25432-multilayer-words.py',
              'check-v25433-word-stencil.py','check-v25434-registration.py',
              'check-v25435-colors.py'):
        assert i['gate'] in (r/'tests'/t).read_text(),f'stale inherited release gate: {t}'
print('PASS release metadata, Gradle/NAS identity and inherited version checks')

#!/usr/bin/env python3
from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1]
a=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
b=(root/'BUILD_ON_NAS.sh').read_text()
g=(root/'app/build.gradle').read_text()
info=json.loads((root/'tests/release-info.json').read_text())
assert info['versionCode']>=356
assert info['gate'] in b and info['apk'] in b and info['versionName'] in g and ('versionCode '+str(info['versionCode'])) in g
assert 'settings.substrateAware=true' not in a
assert 'settings.substrateAware=false;if(alternateActive())' in a
assert 'newStyleSelectedLayer=-1;setToolInternal(2);requestAlternate();return;' not in a
assert 'VinylCrashReport.install(getApplicationContext())' in a
assert 'VinylCrashReport.breadcrumb(' in a
assert 'Copy last crash report' in a
assert 'operations.length!=NewStyleColorSlots.slotCount(newStyleId,newStyleVariant,newStyleColorCount)' in a
assert 'tessSelectedLayer<tessellationResult.counts.length' in a
assert 'Math.min(tessLayerCount,tessColors.length)' in a
assert 'Math.min(tonalLayerCount,tonalColorOverrides.length)' in a
assert 'alternateReadyGeneration!=alternateGeneration' in a
assert 'Math.min(VinylEngine.PATTERNS.length-1,json.optInt("pattern"' in a
c=(root/'app/src/main/java/com/halftonestudio/VinylCrashReport.java').read_text()
assert 'previous.uncaughtException(thread, failure)' in c
assert 'getFilesDir()' in c and 'FileOutputStream' in c and 'getPrimaryClip()' not in c
print('PASS RC78 crash capture, result bounds, pattern validation, material inclusion and metadata')

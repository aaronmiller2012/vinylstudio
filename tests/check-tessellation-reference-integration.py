#!/usr/bin/env python3
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
s=r/'app/src/main/java/com/halftonestudio'
engine=(s/'TessellationMotifEngine.java').read_text()
registry=(s/'StudioThumbnailRegistry.java').read_text()
activity=(s/'StudioActivity.java').read_text()
variants=(s/'NewStyleVariantPolicy.java').read_text()
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=459
assert meta['apk'].startswith('VinylHalftoneStudio-v'+meta['versionName']+'-') and meta['versionName'].startswith('3.') and meta['apk'].endswith('.apk')
assert ('versionCode '+str(meta['versionCode'])) in (r/'app/build.gradle').read_text()
assert 'if(!TessellationMotifEngine.selectablePattern(i))continue;' in activity
assert 'TessellationMotifEngine.PATTERN_SLOT_COUNT' in activity
assert 'TessellationReferenceLayouts.tiles(pattern,w,h,unit)' in engine
assert 'TessellationReferenceLayouts.insetConcave(cropped,margin)' in engine
assert 'TessellationMotifEngine.selectablePattern(candidate)' in variants
assert 'TESSELLATION_COUNT = 28' in registry
assert 'TessellationSecondReferenceLayouts.tiles(pattern,w,h,unit)' in engine
assert (s/'TessellationSecondReferenceLayouts.java').is_file()
assert 'tests/run-tessellation-reference-gate.sh' in (r/'tests/run-v3-beta4-host-gate.sh').read_text()
assert (r/'app/src/test/java/com/halftonestudio/TessellationReferenceUnitTest.java').exists()
assert meta['apk'] in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS Beta8 Trim5 Android registry, render, JUnit and release version wiring')

#!/usr/bin/env python3
"""RC87 release identity and Android wiring assertions; complements host behavior tests."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/halftonestudio'
meta=json.loads((root/'tests/release-info.json').read_text())
assert meta['versionCode']>=365 and meta['versionName'].startswith(meta['version'])
assert meta['apk'].endswith('.apk') and meta['gate'].startswith('tests/run-')
assert meta['gate'] in (root/'BUILD_ON_NAS.sh').read_text()
assert meta['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
assert meta['versionName'] in (root/'app/build.gradle').read_text()
loader=(src/'StartupPhotoLoader.java').read_text()
activity=(src/'StudioActivity.java').read_text()
controller=(src/'StudioRenderController.java').read_text()
tiled=(src/'TiledImageLevels.java').read_text()
assert 'PhotoDecodeBudget.plan(' in loader
assert 'EDIT_MAX_SIDE' in loader and 'ORIGINAL_PREVIEW_MAX_SIDE' in loader
assert 'if(loaded!=null)loaded.discard();' in activity
assert 'projectPhotoStore.markExistingOriginal(originalPhoto,getFilesDir());' in activity
assert 'memoryBudget.run(lane,task)' in controller
assert 'float[] lum=new float[w*Math.min(h,BAND_ROWS+2)]' in tiled
assert (src/'RenderWorkBudget.java').is_file()
print('PASS RC87 Android wiring, safeguards, build identity')

#!/usr/bin/env python3
"""Natural Confetti integration contracts, preserving retired IDs and manual palette order."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
def read(p):return (root/p).read_text()
meta=json.loads(read('tests/release-info.json'))
assert meta=={'version':'2.55.20','versionCode':432,'versionName':'2.55.20-natural-confetti-rc155',
 'apk':'VinylHalftoneStudio-v2.55.20-NaturalConfetti-RC155.apk',
 'gate':'tests/run-rc155-natural-confetti-gate.sh'}
engine=read('app/src/main/java/com/halftonestudio/PatternPortraitTrioEngine.java')
scatter=read('app/src/main/java/com/halftonestudio/FallenConfettiEngine.java')
ui=read('app/src/main/java/com/halftonestudio/StudioActivity.java')
suite=read('app/src/main/java/com/halftonestudio/VinylStyleSuite.java')
thumb=read('app/src/main/java/com/halftonestudio/StudioStylePreview.java')
assert 'FallenConfettiEngine.render(surface,o,colors)' in engine
assert 'if(o.kind==CONFETTI)sortPaletteDarkToLight(colors)' not in engine
assert 'if(c.style==CONFETTI||c.style==CHAINMAIL)' in suite
assert 'case CELTIC_BRAID:case ARCHITECTURAL:case SPIDER_WEB:return true' in suite
assert 'for(int id:new int[]{VinylStyleSuite.CONFETTI,VinylStyleSuite.CHAINMAIL})' in ui
assert 'static final int DISC=0, PAPER=1, STRIP=2' in scatter
assert 'static int[] autoPalette' in scatter
assert 'surface.fillValid(0)' in scatter
assert 'if(type==15){ // Natural confetti' in thumb
assert 'RC155' in read('transfer/PHONE_BUILD_AND_RECEIVE.sh')
assert 'RC155' in read('transfer/NAS_STAGE_BUILD.sh')
print('PASS RC155 metadata, stable style IDs, natural scatter, 3 shapes, silhouette base, swatch identity and transfer')

#!/usr/bin/env python3
"""Natural Confetti integration contracts, preserving retired IDs and manual palette order."""
from pathlib import Path
import json
import re
root=Path(__file__).resolve().parents[1]
def read(p):return (root/p).read_text()
meta=json.loads(read('tests/release-info.json'))
assert meta=={'version':'2.55.22','versionCode':434,'versionName':'2.55.22-sliders-palette-layers-rc157',
 'apk':'VinylHalftoneStudio-v2.55.22-SliderPaletteLayers-RC157.apk',
 'gate':'tests/run-rc157-sliders-palette-layers-gate.sh'}
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
assert 'RC157' in read('transfer/PHONE_BUILD_AND_RECEIVE.sh')
assert 'RC157' in read('transfer/NAS_STAGE_BUILD.sh')
assert re.search(r'\brnd\.nextFloat\(', scatter) is None, 'API 35 nextFloat call is still present'
assert re.search(r'\brnd\.nextBoolean\(', scatter) is None, 'API 35 nextBoolean call is still present'
assert 'static float unitFloat(SplittableRandom rnd)' in scatter
assert 'rnd.nextInt() >>> 8' in scatter
assert 'minSdk 26' in read('app/build.gradle')
assert 'lint-baseline.xml' not in read('app/build.gradle')
assert 'SliderRangePolicy.value(progress,min,max,increment)' in ui
assert 'colorSwatchGrid(g,layerColors,layerNames,layerPopulated,newStyleSelectedLayer' in ui
assert 'NewStyleLayerPolicy.isStructuralLayer(newStyleId,index)' in ui
assert 'o.maxWidthFraction=StyleKit.clampF(.47f+.24f*c.weightMm' in suite
assert 'CONFETTI_STACK = 8' in read('app/src/main/java/com/halftonestudio/PaletteStylePolicy.java')
print('PASS RC157 metadata, slider wiring, style tone policies, structural layer protection and API 26 compatibility')

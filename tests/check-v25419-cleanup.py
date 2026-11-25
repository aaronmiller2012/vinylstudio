#!/usr/bin/env python3
"""Ensure retired art styles do not remain reachable in the shipped UI/engine."""
from pathlib import Path
import json
root=Path('app/src/main/java/com/halftonestudio')
s=(root/'StudioActivity.java').read_text()
for name in ('DotToDotEngine.java','DotToDotAndroid.java'):
    assert not (root/name).exists(),f'retired engine still exists: {name}'
for forbidden in ('dotToDotMode','dotToDotPortrait','dotToDotResult',
                  'dotToDotControls','selectDotToDotPortrait','exportDotToDot',
                  'DotToDotEngine','DotToDotAndroid','Dot-to-Dot Portrait'):
    assert forbidden not in s.replace('json.optBoolean("dotToDotPortrait",false)',''),f'leftover feature: {forbidden}'
assert 'if(json.optBoolean("dotToDotPortrait",false)){' in s, 'legacy portrait fallback missing'
assert 'if(ContinuousLineModes.curveFamily(curve)<0)' in s, 'curve safety gate missing'
assert 'for(int i=0;i<cache.fills.length;i++)' in s, 'new style preview missing'
assert 'if(maze!=null)' in s, 'continuous style preview missing'
assert '"VinylStudio-specialty-cut-layers.zip"' in s, 'normal export filename missing'
assert 'putZip(zip,"cut-safety-review.csv"' in s or 'cut-safety-review.csv' in s
assert 'android.useAndroidX=true' in Path('gradle.properties').read_text().splitlines(), 'AndroidX must remain enabled for MediaPipe Tasks Vision runtime dependencies'
meta=json.loads(Path('tests/release-info.json').read_text())
assert (meta['versionCode'],meta['gate']) in ((331,'tests/run-v25419-cleanup-gate.sh'),(332,'tests/run-v25420-library-gate.sh'),(333,'tests/run-v25421-project-safety-gate.sh'),(334,'tests/run-v25422-architecture-gate.sh'),(335,'tests/run-v25423-performance-gate.sh'),(336,'tests/run-v25424-interface-gate.sh'),(337,'tests/run-v25425-textdetail-gate.sh'),(338,'tests/run-v25426-multilayer-text-gate.sh'),(339,'tests/run-v25427-textcolor-gate.sh'),(340,'tests/run-v25428-regions-gate.sh'),(341,'tests/run-v25429-ascii-shapes-gate.sh'),(342,'tests/run-v25430-ascii-vector-gate.sh'),(343,'tests/run-v25431-text-shapes-gate.sh'),(344,'tests/run-v25432-multilayer-words-gate.sh'),(345,'tests/run-v25433-word-stencil-gate.sh'),(346,'tests/run-v25434-registration-gate.sh'),(347,'tests/run-v25435-colors-gate.sh'),(348,'tests/run-v25436-export-colors-gate.sh'),(349,'tests/run-v25437-solid-snip-gate.sh'),(350,'tests/run-v25438-auto-silhouette-gate.sh'),(351,'tests/run-v25439-silhouette-l1-gate.sh'),(352,'tests/run-v25440-physical-layers-gate.sh'),(353,'tests/run-v25441-visible-controls-gate.sh'),(354,'tests/run-v25442-palette-tap-gate.sh','tests/run-v25443-cut-sheet-gate.sh'),(355,'tests/run-v25443-cut-sheet-gate.sh'),(356,'tests/run-v25444-reliability-gate.sh'),(357,'tests/run-v25445-glass-portrait-gate.sh','tests/run-v25446-glass-cutfix-gate.sh'),(358,'tests/run-v25446-glass-cutfix-gate.sh'),(359,'tests/run-v25447-palette-harmony-gate.sh'),(360,'tests/run-v25448-integrity-gate.sh'),(361,'tests/run-v25449-guide-gate.sh'),(362,'tests/run-v25450-performance-gate.sh'),(363,'tests/run-v25451-security-gate.sh'),(364,'tests/run-v25452-stability-gate.sh'))
assert meta['versionName'] in Path('app/build.gradle').read_text()
print('PASS: old numbered mode removed, saved-project fallback retained, art and line engines intact')

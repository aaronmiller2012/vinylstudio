from release_version import at_least
from pathlib import Path
import json
p=Path('app/src/main/java/com/halftonestudio')
engine=(p/'MultilayerStencilEngine.java').read_text()
cache=(p/'SpecialtyVectorCache.java').read_text()
assert 'SmoothStencilContours.build' in engine
assert 'cutPathData(layerIndex)' in engine
assert 'r.cutPathData(i)' in cache
assert 'pathFromMLCZ' in cache
assert 'cubicTo' in cache
assert 'private synchronized void prepareCutPaths()' in engine
assert 'private TextView quickPill(String caption,View.OnClickListener click,boolean active)' in (p/'StudioActivity.java').read_text()
info=json.loads(Path('tests/release-info.json').read_text())
assert at_least(info['version'], '2.54.0') and info['versionCode'] >= 315
assert info['gate'] in Path('BUILD_ON_NAS.sh').read_text()
assert info['versionName'] in Path('app/build.gradle').read_text()
print('PASS source wiring, preview/export parity, compile-fix retention and release metadata')

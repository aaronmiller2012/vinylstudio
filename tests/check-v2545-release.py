from release_version import at_least
from pathlib import Path
import json
src=Path('app/src/main/java/com/halftonestudio')
s=(src/'SmoothStencilContours.java').read_text()
engine=(src/'MultilayerStencilEngine.java').read_text()
assert 'acceptGroups(selected,candidates,eligible' in s
assert 'int[] budget={40}' in s
assert 'Math.max(requestedMm,cell*1.45)' in s
assert 'float curveMm=options.curveRadiusMm' in engine
assert 'final float[] reductions={.72f,.46f,.24f,0f}' in engine
assert 'r.cutPathData(i)' in (src/'SpecialtyVectorCache.java').read_text()
assert 'quickPill(caption,(View v)->click.run(),active)' in (src/'StudioActivity.java').read_text()
meta=json.loads(Path('tests/release-info.json').read_text())
assert at_least(meta['version'], '2.54.0') and meta['versionCode'] >= 317
assert meta['versionName'] in Path('app/build.gradle').read_text()
assert meta['gate'] in Path('BUILD_ON_NAS.sh').read_text()
assert meta['apk'] in Path('BUILD_ON_NAS.sh').read_text()
print('PASS v2.54.5 clean contour source wiring, current APK metadata, callback fix, shared preview/export')

from release_version import at_least
from pathlib import Path
import json
root=Path('app/src/main/java/com/halftonestudio')
engine=(root/'MultilayerStencilEngine.java').read_text()
mesh=(root/'SharedStencilBoundaries.java').read_text()
preview=(root/'SpecialtyVectorCache.java').read_text()
meta=json.loads(Path('tests/release-info.json').read_text())
assert 'SharedStencilBoundaries.create(' in engine
assert 'mesh.path(cut.loops)' in engine
assert 'mesh.path(top.loops)' in engine
assert 'sharedVisible=visible' in engine
assert 'r.cutPathData(i)' in preview and 'r.visiblePathData(i)' in preview
assert 'cubicTo' in preview
assert 'smoothedVertexCount()' in mesh
assert at_least(meta['version'], '2.54.0') and  meta['versionCode'] >= 318
assert meta['versionName'] in Path('app/build.gradle').read_text()
assert meta['gate'] in Path('BUILD_ON_NAS.sh').read_text()
assert meta['apk'] in Path('BUILD_ON_NAS.sh').read_text()
assert meta['gate'] in Path('BUILD_ON_NAS.sh').read_text()
assert 'quickPill(caption,(View v)->click.run(),active)' in (root/'StudioActivity.java').read_text()
print('PASS v2.54.6 shared visible/cut paths, cubic preview/export, metadata, Projects callback')

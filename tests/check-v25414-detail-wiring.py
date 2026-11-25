from release_version import at_least
from pathlib import Path
import json
D=Path('app/src/main/java/com/halftonestudio')
meta=json.loads(Path('tests/release-info.json').read_text())
assert (D/'PhotoCellImportance.java').is_file()
assert 'generateAdaptiveGrid(' in (D/'TessellationEngine.java').read_text()
assert 'refineSites(' in (D/'StainedGlassEngine.java').read_text()
assert 'o.maxPieces=160+(int)(1000*f)' in (D/'VinylStyleSuite.java').read_text()
assert meta['versionCode']>=326 and at_least(meta['version'], '2.54.0')
assert meta['gate'] in Path('BUILD_ON_NAS.sh').read_text()
assert meta['gate'] in Path('BUILD_ON_NAS.sh').read_text()
assert meta['apk'] in Path('BUILD_ON_NAS.sh').read_text()
print('PASS adaptive cell detail and active Gradle/NAS release wiring')

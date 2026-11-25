from release_version import at_least
from pathlib import Path
import json
r=Path('.')
e=(r/'app/src/main/java/com/halftonestudio/TessellationEngine.java').read_text()
s=(r/'app/src/main/java/com/halftonestudio/MultiColorStippleEngine.java').read_text()
m=json.loads((r/'tests/release-info.json').read_text())
assert 'quantile-balanced brightness' in e and 'matcher.choose(seeds.get(i).color)' in e
assert 'PhotoColorMatcher' in s and 'tidyIsolatedOwners' in s
assert 'combinedSvg()' in e and 'first.indexOf("<g ")' in e
assert at_least(m['version'], '2.54.0') and m['versionCode'] >= 319
assert m['versionName'] in (r/'app/build.gradle').read_text()
assert m['apk'] in (r/'BUILD_ON_NAS.sh').read_text()
assert m['gate'] in (r/'BUILD_ON_NAS.sh').read_text()
assert 'quickPill(caption,(View v)->click.run(),active)' in (r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
print('PASS release metadata, photo-color routing, original Projects callback fix')

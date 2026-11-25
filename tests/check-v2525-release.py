from pathlib import Path
import re,json
p=Path(__file__).resolve().parents[1]
g=(p/'app/build.gradle').read_text()
m=json.loads((p/'tests/release-info.json').read_text())
assert f"versionCode {m['versionCode']}" in g and m['versionName'] in g
assert m['apk'] in (p/'BUILD_ON_NAS.sh').read_text()
assert f"commandLine 'bash', '{m['gate']}'" in g
assert "tasks.named('preBuild').configure { dependsOn tasks.named('hostRegression') }" in g
assert "providers.gradleProperty('withHostRegression')" in g
assert f"bash {m['gate']}" in (p/'BUILD_ON_NAS.sh').read_text()
s=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'for(float y=0;y<hh;y+=checker)' not in s
assert 'for(int yy=0;yy<r.heightMm;yy+=10)' not in s
assert 'Path p=draftSnipPath;p.rewind()' in s
assert 'Path outline=draftSnipPath;outline.rewind()' in s
assert 'private final ProjectPhotoStore projectPhotoStore' in s
assert 'executor.shutdownNow()' not in s
assert 'private final StudioRenderController renderController' in s
assert 'Path[] paths' in s and 'cv.drawPath(cache.paths[li],paint)' in s
assert not (p/'app/src/main/java/com/halftonestudio/PortraitCarrierScreen.java').exists()
print('PASS canvas, persistence, worker ownership, release metadata, and no old portrait engine')

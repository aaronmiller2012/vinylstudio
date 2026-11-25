from pathlib import Path
import hashlib
p=Path(__file__).resolve().parents[1]
a=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(p/'app/src/main/java/com/halftonestudio/VinylEngine.java').read_text()
l=(p/'app/src/main/java/com/halftonestudio/LineArtEngine.java').read_text()
t=(p/'app/src/main/java/com/halftonestudio/LinePresenceTracker.java').read_text()
for v in ('Light-area lines · %','settings.lineHighlightRetention=v/100f',
          'out.put("lineHighlightRetention",s.lineHighlightRetention)',
          'json.optDouble("lineHighlightRetention",0)'):assert v in a,v
assert 'public float lineHighlightRetention=0f' in e
assert 's.lineHighlightRetention=lineHighlightRetention' in e
assert 's.lineHighlightRetention' in l
assert 'float highlightRetention)' in t and 'highlightEligible' in t
for name,digest in {
 'MultiColorStippleEngine.java':'dddcfa8c90fb79b8be60a8f87d6a59ef5adcdca48a1c8580ab1fc107eb5ea6c9',
 'TessellationEngine.java':'a31e2c7a95aa074582a4469b6cb701bdcfe8cdb4f2d9e34771c4fa7669eb9f5d'
}.items():assert hashlib.sha256((p/'app/src/main/java/com/halftonestudio'/name).read_bytes()).hexdigest()==digest,name
assert 'versionCode 300' in (p/'app/build.gradle').read_text()
assert 'VinylHalftoneStudio-v2.52.1-LineVisibility-RC22.apk' in (p/'BUILD_ON_NAS.sh').read_text()
print('PASS v2.52.1 line slider, JSON, copy, guarded engines and APK metadata')

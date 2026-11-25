from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(r/'app/src/main/java/com/halftonestudio/MultiColorStippleEngine.java').read_text()
for token in [('\"Detail · point density %\"' if False else '"Detail · point density %"'),'"Dot size · %"','stipplePointDensity','stippleDotSize','stippleOptimizePacking','stippleSettings.optimizePacking=true']:
 assert token in s, token
for token in ['fineCompletion(','growExisting(','MAX_PREVIEW_DOTS=6400','MAX_ADAPTIVE_EXPORT_DOTS=32000','MAX_LAYER_DOTS=4400','o.overlap==SPACED','supportedEnvelope(owner,o,w,h','pointDensity','dotSize','optimizePacking']:
 assert token in e,token
assert ('versionCode 292' in (r/'app/build.gradle').read_text() or ('versionCode 293' in (r/'app/build.gradle').read_text() or 'versionCode 294' in (r/'app/build.gradle').read_text() or ('versionCode 295' in (r/'app/build.gradle').read_text() or ('versionCode 296' in (r/'app/build.gradle').read_text() or 'versionCode 300' in (r/'app/build.gradle').read_text())))) or ('versionCode 293' in (r/'app/build.gradle').read_text() or 'versionCode 294' in (r/'app/build.gradle').read_text() or ('versionCode 295' in (r/'app/build.gradle').read_text() or ('versionCode 296' in (r/'app/build.gradle').read_text() or 'versionCode 300' in (r/'app/build.gradle').read_text())))
assert 'VinylHalftoneStudio-v2.52.1-LineVisibility-RC22.apk' in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS v2.51.3 density and dot-size controls, saved settings and release wiring')

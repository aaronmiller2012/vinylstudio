from pathlib import Path
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(r/'app/src/main/java/com/halftonestudio/MultiColorStippleEngine.java').read_text()
assert ('"Fill · target eligible coverage %"' in a or '"Target eligible-area coverage · %"' in a)
assert 'stippleAdaptiveCoverage' in a and 'stippleTargetCoverage' in a
assert 'stippleSettings.adaptiveCoverage=!stippleSettings.adaptiveCoverage' in a
assert 'adaptiveCompletion(src,owner,w,h,o,initialDesired,desired,eligibleTotal,eligibleCounts,' in e
assert 'cov.perLayer[li]>=target*eligibleCounts[li]' in e
assert 'grid.free(x,y,radius,li,gap,o.overlap==SPACED)' in e
assert 'supportedEnvelope(owner,o,w,h,x,y,radius)' in e
assert 'per-color=' in e
assert ('versionCode 292' in (r/'app/build.gradle').read_text() or ('versionCode 293' in (r/'app/build.gradle').read_text() or 'versionCode 294' in (r/'app/build.gradle').read_text() or ('versionCode 295' in (r/'app/build.gradle').read_text() or ('versionCode 296' in (r/'app/build.gradle').read_text() or 'versionCode 300' in (r/'app/build.gradle').read_text()))))
assert 'VinylHalftoneStudio-v2.52.1-LineVisibility-RC22.apk' in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS v2.51.2 adaptive coverage UI, serialization, engine and build metadata')

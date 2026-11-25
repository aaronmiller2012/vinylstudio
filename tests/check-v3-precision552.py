from pathlib import Path
p=Path('app/src/main/java/com/halftonestudio')
s=(p/'StudioActivity.java').read_text()
render=s.split('private void startAlternateRender(boolean draft)',1)[1].split('private void export',1)[0]
assert 'VinylCutPreflight.inspect(layers,i,widthLimit,gapLimit)' in render
assert render.index('VinylCutPreflight.inspect(layers,i,widthLimit,gapLimit)')<render.index('postUi(()->{if(token!=alternateGeneration)return;')
assert 'completedTonalConcerns' in render and 'imageAdjustments.bridgeMm' in render
assert 'VinylCutPreflight.inspect(layers,i,minimumCutMm,StyleCutGeometryPreflight.exportGapMm(minimumGapMm,minimumCutMm))' in s
assert 'return result.isEmpty(index)?0:result.cutContourCount(index);' in s
assert 'result.cutContourCount(i)' in (p/'LayerCutCounts.java').read_text()
metrics=(p/'SpecialtyArtworkMetrics.java').read_text()
assert 'tiles.stipple.options.order[pos]' in metrics and 'in.piece(slots[t.layer]' in metrics
assert 'PrecisionCurveSampler.cubic' in metrics
assert 'catch(java.util.concurrent.CancellationException cancelled){throw cancelled;}' in metrics
for f in ('BUILD_ON_NAS.sh','BUILD_PLAY_BUNDLE_ON_NAS.sh','.github/workflows/verify.yml','app/build.gradle'):
    text=Path(f).read_text();assert 'tests/run-v3-precision552-gate.sh' in text and 'tests/run-v3-reliability551-gate.sh' in text,f
assert 'com.halftonestudio.SpecialtyArtworkMetricsTest' in Path('tests/run-v3-vector-boolean-gate.sh').read_text()
print('PASS 552 physical layer order, worker-only tonal clearance, contour source and release wiring')

assert "com.halftonestudio.V3PrecisionSizeMatrixTest" in Path("tests/run-v3-precision552-gate.sh").read_text()

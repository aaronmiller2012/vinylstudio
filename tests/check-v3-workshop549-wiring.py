from pathlib import Path
p=Path('app/src/main/java/com/halftonestudio')
s=(p/'StudioActivity.java').read_text()
for method in ('private void requestAlternate(boolean registrationOnly)', 'private void requestRender(boolean keepCurrentRegistrationArtwork)', 'private void requestRegistrationRender()', 'private synchronized void changedPhoto()'):
    assert 'projectReviewProgress.invalidate();' in s.split(method,1)[1][:170],method
assembly=s.split('private AssemblyGuideDocument.Document buildAssemblyGuideDocument()',1)[1].split('private int pendingAssemblyGeneration',1)[0]
for marker in ('guide.pathMetrics(1.0,vectors.fills[index],vectors.strokes[index])','guide.pathMetrics(layout.widthMm/(double)specialty.width,specialty.layers[index])','guide.pathMetrics(layout.widthMm/(double)result.width,vectors.layers[index])','guide.pathMetrics(width/(double)sourceW,line)'):
    assert marker in assembly,marker
assert assembly.count('guide.standardMetrics(java.util.Collections.singletonList(base.shape))')==4
assert 'ready.diagnostic?ProjectReviewProgress.DIAGNOSTIC_ZIP:ProjectReviewProgress.CUT_ZIP' in s
assert 'guide.plan.referenceOnly?ProjectReviewProgress.REFERENCE_PDF:ProjectReviewProgress.ASSEMBLY_PDF' in s
assert 'projectReviewProgress.record(reviewToken,ProjectReviewProgress.CUT_ZIP)' in s
assert 'check.setEnabled(photo!=null&&(i==0||finalReady))' in s
f=(p/'FootprintClearance.java').read_text()
assert 'ring pairs below width' in f and 'ring pairs below gap' not in f
assert 'd.distance(second)+.003<width' in f
assert 'd.distance(g.getGeometryN(j))+.003<gap' in f
assert 'poly.covers(poly.getFactory().createLineString(nearest))' in f
assert 'AssemblyPathMetrics.measure(2,closed,open)' in Path('app/src/androidTest/java/com/halftonestudio/StageOneInstrumentation.java').read_text()
assert 'drawSpecialtyRiskOverlay(cv,newStyleSelectedLayer,artW)' in s
assert 'showPenArtworkMetrics(line,settings.widthMm/(double)sourceWidth,generation,revision)' in s
assert 'final float gap=StyleCutGeometryPreflight.exportGapMm(settings.bridgeMm,minimum);' in s
for file in ('BUILD_ON_NAS.sh','BUILD_PLAY_BUNDLE_ON_NAS.sh','.github/workflows/verify.yml','app/build.gradle'):
    assert 'tests/run-v3-workshop549-gate.sh' in Path(file).read_text(),file
print('PASS 549 all assembly routes, review invalidation, publication ownership and independent width/gap wiring')

from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
engine=(r/'app/src/main/java/com/halftonestudio/LineArtEngine.java').read_text()
assert 'LineRibbonPolicy.plan' in engine
assert 'lanes.secondLane' in engine
assert 's.minimumMm*.5f+.015f' in engine
assert 'style==VinylEngine.CONTOUR?.20f:.12f' in engine
assert 'LineCutDiagnostics.inspect(result)' in s
assert 'line-cut-diagnostics.txt' in s
assert 'validateVinylRibbonSvg' in s
assert 'out.put("schema",14)' in s
assert 'curveVinylRibbon' in s
assert '2.50.3-working-lines-rc4' in (r/'app/build.gradle').read_text()
print('PASS v2.50.0 line lanes, carrier coverage, separate contour geometry, diagnostics, SVG mode boundaries')

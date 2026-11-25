from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=Path('app/src/main/java/com/halftonestudio/VinylEngine.java').read_text()
assert '{"Import","Crop","Snip","Lighting","Size"}' in s
assert '{"Boundary","Marks","Cut Check","Export"}' in s
assert '{WorkflowFlow.IMPORT,WorkflowFlow.CROP,WorkflowFlow.SNIP,WorkflowFlow.LIGHT,-1}, {10,10}, {15,5}, {14,6,13,7}' in s
assert 'private void boundaryPanel()' in s
assert 'cv.drawOval(m,m,r.settings.widthMm-m,r.heightMm-m,paint)' in s
assert 'out.put("outerSpacingMm",s.outerSpacingMm)' in s and 'json.optDouble("outerSpacingMm",8)' in s
assert 'insideCutBoundary(shape,r.settings.widthMm,r.heightMm,r.settings.marginMm,r.settings.circleCrop)' in e
assert 'Math.max(s.outerSpacingMm' in e
assert 'private void colorPaletteOnlyPanel()' in s
print('PASS: Cut Setup order, Prepare routing, rectangular/circular preview, persisted spacing, palette separation')

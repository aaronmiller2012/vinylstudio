from pathlib import Path
p=Path('app/src/main/java/com/halftonestudio')
s=(p/'StudioActivity.java').read_text()
pdf=(p/'AssemblyGuideDocument.java').read_text()
assert 'section(host,"Manufacturer vinyl · physical material references")' in s
assert 'ManufacturerVinylPalettes.colors(ManufacturerVinylPalettes.fromKey(key))' in s
# Beta 24's prepared cut ZIP skips line ribbon diagnostics only for the
# solid Snip base-only export. In every other mode, an incomplete scan must
# fail closed before the Android Save picker can create a document.
prepare=s.split('private PreparedStandardCutZip prepareStandardCutZip(',1)[1].split('private void writeProject(',1)[0]
assert 'LineCutDiagnostics.Report lineReview=baseOnly?null:LineCutDiagnostics.inspect(result);' in prepare
assert 'if(!baseOnly&&!lineReview.complete)' in prepare
assert 'throw new IOException("Line intersection inspection exceeded its budget;' in prepare
assert 'if(!baseOnly&&lineReview.selfIntersections>0)' in prepare
assert 'drawWorkflowPage(checklist.getCanvas(),guide)' in pdf
assert 'footer(c,pageNumber,guide.pages.size()+2)' in pdf
assert 'tonalSmoothingRadius(' in (p/'MultilayerStencilEngine.java').read_text()
assert 'nested concentric' not in (p/'StandardCutSafety.java').read_text().lower() or 'annular gap' in (p/'StandardCutSafety.java').read_text()
print('PASS A/B UI and export wiring')

#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
pkg=root/'app/src/main/java/com/halftonestudio'
doc=(pkg/'AssemblyGuideDocument.java').read_text()
assert 'PhysicalSheetParity.requireSameBoard(finished.widthMm,finished.heightMm,' in doc
assert 'AssemblyMaterialPlan.from(metadata,finished.widthMm,finished.heightMm)' in doc
assert 'Material planning:' in doc and 'guide.material.disclaimer()' in doc
assert 'guide.material.sheetWidthMm/25.4f' in doc
assert 'Reference only' in doc
print('PASS A3/B1 PDF integration guards and material notices')

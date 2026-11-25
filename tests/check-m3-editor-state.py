#!/usr/bin/env python3
"""M3 wiring invariants that behavioral host tests cannot reach inside StudioActivity.
Behavior is tested by V3M3StyleSelectionTest, V3M3LiveColorPreviewTest,
V3M3LibraryIdHandoffTest and the on-device StageOneInstrumentation cases."""
import re
from pathlib import Path
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
FIELDS=('continuousStyle','tonalStencilMode','mazePortraitMode','tessellationMode','layeredCellMode',
        'offsetCellMode','bottleCapMode','fishScaleMode','overprintCellKind','newStyleId')
m=re.search(r'private void applyStyleSelection\(StyleSelection s\)\{(.*?)\n    \}',a,re.S)
assert m,'applyStyleSelection missing'
body=m.group(0)
outside=a.replace(body,'')
for f in FIELDS:
    assert re.search(r'\b'+f+r'\s*=[^=]',body), f+' not derived from StyleSelection'
    for w in re.finditer(r'(?<![\w.])'+f+r'\s*=(?!=)',outside):
        line=outside[outside.rfind('\n',0,w.start())+1:outside.find('\n',w.start())].strip()
        if line.startswith('private '):continue  # declaration / initializer
        # The loader sets the suite id early (per-suite restore reads it); the
        # StyleSelection applied at the end of restoreArtworkModes is authoritative.
        if line=='newStyleId=VinylStyleSuite.restoredStyleId(storedStyleId);':continue
        raise AssertionError('artwork mode written outside applyStyleSelection: '+line)
assert 'applyStyleSelection(StyleSelection.fromLegacyFlags(' in a,'loader must normalize through StyleSelection'
# D15: the colour dialog previews through LiveColorPreview and never writes the layer until Apply.
assert 'liveColorPreview.update(color)' in a and 'current.color=color' not in a
assert 'liveColorPreview.colorFor(li,' in a
# D19 / D14 wiring.
assert re.search(r'pristineEditorJson=snapshot\(\);\s*\n\s*restoreLocal\(\);',a),'pristine snapshot must precede restoreLocal'
assert 'restorePristineEditor();' in a.split('private void closeCurrentProject(){')[1].split('\n    }\n')[0]
assert 'LibraryIdHandoff.record(libraryHandoffStore(),entry.id,entry.name,savedPhotoToken,editorIdAtSave)' in a
print('PASS M3 editor-state wiring: single style write path, draw-only colour preview, D19 reset, D14 handoff')

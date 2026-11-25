#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
g=(r/'app/src/main/java/com/halftonestudio/MultiColorStippleEngine.java').read_text()
t=(r/'app/src/main/java/com/halftonestudio/TessellationEngine.java').read_text()
assert 'stippleSettings.overlap=MultiColorStippleEngine.OVERPRINT' in a
assert 'stippleSettings.assignment=MultiColorStippleEngine.NEAREST' in a
assert 'Photo-derived' in a and 'suggestStipplePhotoColors()' in a
assert 'StipplePhotoPalette.derive(' in a
assert 'stippleSettings.order[physicalOrder]' not in a or 'result.stipple.options.order[physicalOrder]' in a
p=a.index('private void stippleControls(')
e=a.index('    /** Compact tessellation settings',p)
control=a[p:e]
assert 'setSingleChoiceItems' not in control and 'Overlap mode:' not in control and 'Color assignment:' not in control
assert 'MAX_PREVIEW_DOTS=2250' in g and 'MAX_DOTS=10000' in g
assert 'MAX_LAYER_DOTS=4400' in g
assert 'SourceColorStencilQuantizer.quantize' in (r/'app/src/main/java/com/halftonestudio/StipplePhotoPalette.java').read_text()
assert 'swatch-' in t
assert 'v2.50.8' in (r/'BUILD_ON_NAS.sh').read_text() or 'v2.50.9' in (r/'BUILD_ON_NAS.sh').read_text() or 'v2.51.0' in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS v2.50.8 defaults, photo swatches, simpler Adjust, physical stack preview, export safety and saved-mode wiring')

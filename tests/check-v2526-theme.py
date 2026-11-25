#!/usr/bin/env python3
"""RC27 source-level UI integrity checks (not a substitute for Android compilation)."""
from pathlib import Path
import re
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
t=(r/'app/src/main/java/com/halftonestudio/StudioTheme.java').read_text()
x=(r/'app/src/main/res/values/styles.xml').read_text()
uses=set(re.findall(r'StudioTheme\.([A-Z_0-9]+)',a))
defs=set(re.findall(r'static final int\s+([A-Z_0-9]+)\s*=',t))
assert uses and uses<=defs, f'Undefined tokens: {uses-defs}'
assert 'RippleDrawable' in a and 'private Drawable pillBackground' in a
assert 'styleSeekBar(bar)' in a and 'styleHeaderButton(topUndo)' in a
assert 'topRounded(SURFACE,StudioTheme.R_SHEET)' in a
assert 'dp(StudioTheme.H_NAV)' in a and 'dp(StudioTheme.H_SUBTOOL+2)' in a
assert '#5CD6E8' in x and '#0D1117' in x
# Default swatches remain independent from appearance theme.
assert 'int[] colors={0xffffdd00,0xff00aeef,0xffec008c,0xff131313};' in a
assert not (r/'app/src/main/java/com/halftonestudio/PortraitCarrierScreen.java').exists()
print(f'PASS RC27 theme: {len(uses)} tokens, touch feedback, shelf, swatches, status/navigation bars')

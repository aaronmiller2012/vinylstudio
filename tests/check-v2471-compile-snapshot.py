#!/usr/bin/env python3
"""Source-level regression for v2.47.1 revision snapshot compile fix."""
from pathlib import Path
import re
p=Path(__file__).resolve().parents[1]/'app/src/main/java/com/halftonestudio/StudioActivity.java'
s=p.read_text()
a=s.index('private void startAlternateRender(boolean draft){')
b=s.index('renderController.submit(StudioRenderController.Lane.SPECIALTY,',a)
body=s[a:b]
assert body.count('final long sourceRevision=photoRevision;') == 1, 'Missing/duplicated photo revision snapshot'
assert body.index('final Bitmap source=photo;') < body.index('final long sourceRevision=photoRevision;')
assert 'specialtyPrepared(sourceRevision,source,snips,fillMode,circular,draft,imageAdjustments)' in s[b:]
assert 'private synchronized void changedPhoto(){photoRevision++' in s, 'Photo revision invalidation must remain intact'
print('PASS: specialty render captures sourceRevision before async lambda')

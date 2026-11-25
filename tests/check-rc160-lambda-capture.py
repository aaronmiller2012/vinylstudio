#!/usr/bin/env python3
"""Prevent Java lambda capture of locally reassigned photo pixels (RC159 E40)."""
from pathlib import Path
import re
ui=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
start=ui.index('private void ensurePreparedPhotoPalette()')
end=ui.index('private void invalidateStipplePhotoPaletteRequest()',start)
s=ui[start:end]
assert 'if(!ImageLevels.isNeutral(levels))rgba=ImageLevels.apply(rgba,w,h,levels);' in s
assert 'final int[] palettePixels=rgba;' in s
assert 'PhotoPaletteExtractor.extract(palettePixels,w,h)' in s
callback=s.split('handler.post(()->{',1)[1].split('});',1)[0]
assert 'preparedPhotoColorPixels=palettePixels;' in callback
assert 'updateAutomaticSnipFoundation(suggested,palettePixels,false);' in callback
assert not re.search(r'\bpreparedPhotoColorPixels=rgba\b|updateAutomaticSnipFoundation\(suggested,rgba', callback)
print('PASS RC160 effectively-final photo palette callback capture')

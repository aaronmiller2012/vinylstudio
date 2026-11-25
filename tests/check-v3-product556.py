#!/usr/bin/env python3
"""Build 556 wiring: one-upload all-layers SVG for Cricut Design Space and its share action."""
from pathlib import Path
root=Path(__file__).resolve().parents[1];src=root/'app/src/main/java/com/halftonestudio'
w=(src/'CutSheetZipWriter.java').read_text()
assert 'retainForCombined(name,svg);' in w and 'writeCombinedSvg();' in w, 'every SVG cut ZIP must offer the combined sheet'
assert w.index('writeCombinedSvg();')>w.index('throw new FinalCutReviewRequired("Final cut sheet inspection is incomplete.'), \
    'combined SVG is written only after every final-sheet safety check passed'
assert 'CutSvgCompatibility.verify(combined,outputDeviceId);' in w, 'combined SVG must obey the Cricut upload limit'
c=(src/'CombinedLayerSvg.java').read_text()
assert 'FILENAME="00-ALL-LAYERS-one-upload.svg"' in c and '<?xml' not in c
f=(src/'StudioProductFlows.java').read_text()
assert 'CombinedLayerSvg.FILENAME.equals(sheet.getName())' in f and 'shareCombinedSvg(combined)' in f
assert 'setType("image/svg+xml")' in f.split('private void shareCombinedSvg')[1].split('private void shareCutSheets')[0]
assert '00-ALL-LAYERS-one-upload.svg' in (src/'OutputDeviceProfile.java').read_text(), 'Cricut hand-off must name the one-upload file'
s=(root/'app/src/main/res/values/strings.xml').read_text()
for k in ('share_combined_message','action_send_one_svg','action_separate_sheets','share_combined_chooser'):
    assert f'name="{k}"' in s, k
print('PASS 556 one-upload combined SVG, safety ordering, Cricut limit, share wiring')

#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
fragment=s[s.index('    private void snipPanel(){'):s.index('    private void applySnip(){')]
assert all(phrase in fragment for phrase in ('"Keep inside"','"Remove inside"','"Close points"','"Apply snip"','"Discard outline"'))
assert 'if(settings.snips!=null&&!settings.snips.isEmpty()){' in fragment
assert '"Background outside snip"' not in fragment
assert '"Fit"' not in fragment and '"Empty","White","Black"' not in fragment
assert all(old not in fragment for old in ('"Place points"','"Continue to Lighting"','"Background: "'))
assert all(old not in s for old in ('"Start with a photograph"','"Prepare in order:', '"Continue to crop / reframe"','"Continue to Snip"','"Continue to Size"'))
assert 'canvasStage.addView(channelOverlay,channelsP)' not in s
assert 'channelOverlay.setVisibility(View.GONE);' in s
assert 'subtoolScroller.setVisibility(View.GONE)' in s
assert '{"Import","Crop","Snip","Lighting","Size"}' in s
assert 'private void colorPaletteOnlyPanel()' in s and 'section(b,"Mounting surface")' in s and 'section(presets,"Vinyl palettes · "' in s
assert 'out.put("snipFillMode",s.snipFillMode)' in s and 'settings.snips.add(op)' in s
assert 'private void recordSnip()' in s and 'SnipTimeline.State state=forward?' in s
assert 'if(t==8)invalidateToolPanels();' in s, 'Snip card must update after mode change or Close points'
print('PASS: Prepare essentials; S Pen polygon controls; no Fit/background pills, no redundant navigation; undo/redo preserved')

from pathlib import Path
p=Path('app/src/main/java/com/halftonestudio')
a=(p/'StudioActivity.java').read_text()
v=(p/'VinylStyleSuite.java').read_text()
e=(p/'OrganicFlowPortraitEngine.java').read_text()
s=(p/'OrganicFlowCutSmoother.java').read_text()
assert 'static final int DEFAULT = 50;' in s
assert 'slider(host,"Smoothing · simpler cuts"' not in a
assert 'Automatic 50% contour smoothing' in a
assert 'config.organicSmoothing=OrganicFlowCutSmoother.DEFAULT;' in a
assert 'newStyleSmoothing=OrganicFlowCutSmoother.DEFAULT; // Ignore legacy custom slider values' in a
assert 'newStyleSmoothingMemory[i]=OrganicFlowCutSmoother.DEFAULT; // Legacy setting retained in JSON only' in a
# Older projects and backups still read; we do not change the JSON schema.
assert 'out.put("newStyleSmoothing",newStyleSmoothing)' in a
assert 'entry.put("smoothing",newStyleSmoothingMemory[i])' in a
assert 'newStyleSmoothingMemory' in a
assert 'c.organicSmoothing=organicSmoothing' in v
assert 'o.smoothing=StyleKit.clampI(c.organicSmoothing,0,100)' in v
assert 'if(!o.draft)OrganicFlowCutSmoother.discardMicroIslands(' in e
assert 'if(!o.draft)out=OrganicFlowCutSmoother.contour(out,surface,o.minCutMm,o.gapMm,' in e
assert 'alternatePreviewDraft' in a and 'if(!draft)commitMarkReport()' in a
print('PASS fixed 50% UI, legacy project restore, draft export gate and engine compatibility')

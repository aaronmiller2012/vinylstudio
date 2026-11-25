from pathlib import Path
root=Path('app/src/main/java/com/halftonestudio')
activity=(root/'StudioActivity.java').read_text()
suite=(root/'VinylStyleSuite.java').read_text()
photo=(root/'PhotoStyleDefaults.java').read_text()
engine=(root/'BanknoteEngravingEngine.java').read_text()
for text in ('NewStyleVariantPolicy.restore(newStyleId,json.optInt("newStyleVariant",0))',
    'NewStyleVariantPolicy.restore(i,item.optInt("variant",0))',
    'NewStyleVariantPolicy.selectable(id)'):
    assert text in activity, 'Missing integration: '+text
assert 'rebuildPhaseBar' not in activity and 'rebuildWorkflowProgress' not in activity
assert 'final float min=Math.max(0.3f,c.minimumMm)' not in suite
assert 'final float min=Math.max(.3f,Float.isFinite(minCutMm)' not in photo
assert 'o.dashHighlights=c.variant==BanknoteEngravingEngine.HIGHLIGHT_DASH;' in suite
assert 'dashHighlights = false' in engine
print('PASS RC107 source integration checks')

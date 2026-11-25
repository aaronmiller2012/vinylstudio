#!/usr/bin/env python3
"""Ensure a new style never blocks first visible artwork on full cut validation."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
activity=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
engine=(root/'app/src/main/java/com/halftonestudio/OrganicFlowPortraitEngine.java').read_text()
assert 'final boolean firstOrganicPreview=!draft&&VinylStyleSuite.isOrganicFlow(config.style)&&!usableOrganicPreview;' in activity
assert 'specialtyVisibleRevision==revision' in activity
assert 'specialtyVisibleStyle==config.style&&specialtyVisibleVariant==config.variant' in activity
assert 'light.draft=true;' in activity
assert 'newStyleResult=provisional;newStyleVectors=quickVectors;' in activity
assert 'alternatePreviewDraft=true;' in activity
assert 'alternatePreviewDraft=draft' in activity
assert 'alternateReadyGeneration=token' in activity
assert 'if(!o.draft)out=OrganicFlowCutSmoother.contour' in engine
assert 'Draft preview' in engine
print('PASS provisional Organic Flow preview and separate final export geometry')

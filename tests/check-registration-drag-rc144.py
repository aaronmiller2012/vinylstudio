#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
for func,end in [('drawStippleVectors','@Override protected void onDraw'),('drawNewStyleVectors','drawContinuousVectors'),('drawSpecialtyVectors','drawNewStyleVectors')]:
    section=s[s.index('private void '+func):s.index(end,s.index('private void '+func))]
    assert 'drawRegistrationOverlay(cv,artW,artH)' in section, func+' is missing mark overlay'
    assert 'setArtworkTransform(artW,artH,pad)' in section, func+' does not share touch transform'
assert 'viewedPad' in s[s.index('private float[] viewToArtwork'):s.index('private float gestureWidth')]
assert 'r.padMm' not in s[s.index('private float[] viewToArtwork'):s.index('private float gestureWidth')]
assert 'RegistrationPreviewGeometry.issue' in s
assert 'if(t==8||t==5)invalidateToolPanels();' in s
assert 'markDragFixedPad=viewedPad' in s
assert 'if(dragWasRecorded||snipVertex>=0||draggedMark>=0)rollbackGesture()' in s
assert 'if(!editing&&tool==5&&activeWorkspace!=0)' in s
assert 'RegistrationMarks.defaultPositions(gw,gh' not in s
print('PASS RC144: specialty registration overlays and screen-to-artwork mapping')

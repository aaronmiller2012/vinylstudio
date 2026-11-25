#!/usr/bin/env python3
"""Verify artwork-local overlay transforms across renderer branches."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
def section(start,end):
    return s.split('private void '+start+'(',1)[1].split('private void '+end+'(',1)[0]
for entry,end in [('drawSpecialtyVectors','drawNewStyleVectors'),('drawStippleVectors','drawContinuousVectors')]:
    if entry=='drawStippleVectors':
        content=s.split('private void drawStippleVectors(',1)[1].split('@Override protected void onDraw(',1)[0]
    else:
        content=section(entry,end)
    assert 'cv.save();cv.translate(pad,pad);\n            drawRegistrationOverlay(cv,artW,artH);' in content or \
           'cv.save();cv.translate(pad,pad);\n            if(tonalStencilMode||tessellationMode||layeredCellMode)\n                drawRegistrationOverlay(cv,artW,artH);' in content,entry
    assert 'drawSpecialtyRiskOverlay(cv,' in content
    assert 'cv.restore();\n            cv.restore();' in content,entry
for name,end in [('drawNewStyleVectors','drawContinuousVectors'),('drawStandaloneSpecialty','drawSpecialtyRiskOverlay')]:
    content=section(name,end)
    assert 'cv.translate(pad,pad)' in content
    assert 'drawRegistrationOverlay(cv,' in content
pad=s.split('private float previewMarkPad(',1)[1].split('private float[] viewToArtwork(',1)[0]
assert 'RegistrationViewportMath.stablePreviewPadding(artW,artH,' in pad
assert 'RegistrationPreviewGeometry.padding(artW,artH,settings.markSizeMm' not in pad
assert 'markDragFixedPad' in pad and 'marksViewportPad' in pad
assert 'RegistrationViewportMath.clampTouch(x,getWidth(),marginPx)' in s
assert 'RegistrationViewportMath.clampTouch(y,getHeight(),marginPx)' in s
print('PASS RC147: cell, offset cell, stipple, new style, photo overlays are artwork-local')

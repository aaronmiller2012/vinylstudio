#!/usr/bin/env python3
"""Source contract: mark drags must never replace a finished preview with the photo."""
from pathlib import Path
s=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
render=s.split('private void requestRender(boolean keepCurrentRegistrationArtwork){',1)[1].split('/** Lighting is the only processing',1)[0]
assert 'registrationRenderPending=keepCurrentRegistrationArtwork && photo!=null && activeWorkspace!=0' in render
assert 'if(!keepCurrentRegistrationArtwork&&artwork!=null)artwork.clearMarksViewportPad();' in render
assert 'requestAlternate(keepCurrentRegistrationArtwork)' in render
assert 'private void requestAlternate(){requestAlternate(false);}' in s
assert 'if(!registrationOnly)registrationRenderPending=false;' in s
assert 'originalMarks=null;requestRegistrationRender();if(tool==5)setTool(5);}' in s
canvas=s.split('@Override protected void onDraw(Canvas cv){super.onDraw(cv);',1)[1].split('/** Android dispatches S Pen hover',1)[0]
assert 'if(stippleActive()&&(alternateReadyGeneration==alternateGeneration||registrationRenderPending)' in canvas
assert 'if(alternateActive()&&(alternateReadyGeneration==alternateGeneration||registrationRenderPending))' in canvas
assert 'if(alternateActive() && r==null && (alternateReadyGeneration==alternateGeneration||registrationRenderPending)' in canvas
assert 'alternateReadyGeneration!=alternateGeneration && !registrationRenderPending' in canvas
assert 'renderedSerial!=serial && !registrationRenderPending' in canvas
mark=s.split('private float previewMarkPad(float artW,float artH,float pad){',1)[1].split('private float[] viewToArtwork(',1)[0]
assert 'if(tool==5&&marksViewportPad>=0f)return marksViewportPad;' in mark
assert 'if(draggedMark>=0&&markDragFixedPad>=0f)return markDragFixedPad;' in mark
assert 'marksViewportPad=viewedPad;' in s
assert 'void resetZoom(){zoom=1;panX=panY=0;marksViewportPad=-1f;' in s
assert s.count('registrationRenderPending=false;')>=4, 'every standard and specialty success must clear retained mode'
assert 'if(!alternateActive()||alternatePreviewDraft||alternateReadyGeneration<0||' in s, 'export remains blocked until final geometry ready'
print('PASS RC145: drag and release preserve artwork, stable placement, final-render export gating')

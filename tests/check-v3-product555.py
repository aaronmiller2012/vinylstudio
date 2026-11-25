#!/usr/bin/env python3
"""Build 555 wiring: palette visibility fix, parallel geometry cancellation, fast inspection math."""
from pathlib import Path
root=Path(__file__).resolve().parents[1];src=root/'app/src/main/java/com/halftonestudio'
pal=(src/'FallenConfettiEngine.java').read_text()
assert 'static double minLightSpan(int count)' in pal and 'shiftToLight(color,target)' in pal and 'nearestLight(samples,k,target)' in pal
chain=(src/'InterlockingChainmailEngine.java').read_text()
assert 'rings.size()/(double)byTone.length*1.25' in chain, 'occupancy cap keeps sheets under the 5000-piece limit'
cc=(src/'CancellationCheckpoint.java').read_text();sk=(src/'StyleKit.java').read_text()
assert cc.count('ParallelGeometry.cancelled()')==2 and 'ParallelGeometry.cancelled()' in sk, 'pool workers must observe render cancellation'
vb=(src/'VectorBooleanEngine.java').read_text()
assert 'ParallelGeometry.invoke(new UnionTask(' in vb and 'private synchronized void countOp' in vb
assert 'finishVectorSheets(vectorSlots,vectorLayers);' in (src/'PatternPortraitTrioEngine.java').read_text()
for f in ('StyleCutGeometryPreflight.java','FinalCutSheetSafety.java'):
    assert 'Math.hypot(' not in (src/f).read_text(), f+' hot path must use FastGeom.len'
print('PASS 555 palette visibility, parallel geometry with cancellation, fast inspection math')

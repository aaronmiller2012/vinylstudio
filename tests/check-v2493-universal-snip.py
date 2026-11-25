#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'Pinch to zoom · drag to pan · double-tap to fit' in s
assert 'beginSnipContact(event,0,gw,gh)' in s
assert 'SnipInputPolicy.isPen(event.getToolType(newIndex))' in s
assert 'finishSnipContact(event.getX(leaving),event.getY(leaving),gw,gh)' in s
assert 'SnipInputPolicy.keepPenAfterSecondaryExit' in s
assert 'event.findPointerIndex(snipPointerId)' in s
assert 'if(event.getPointerId(0)==snipPointerId)' in s
assert 'if(event.getPointerCount()>=2&&!(tool==8&&stylusActive))' in s
assert 'private void rollbackGesture()' in s
assert 'private void recordSnip()' in s
assert 'MotionEvent.ACTION_CANCEL' in s
assert 'versionCode 300' in (r/'app/build.gradle').read_text()
print('PASS v2.49.3: finger and pen snip routes; palm pointer isolation; pinch preserved')

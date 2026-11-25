from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
g=Path('app/build.gradle').read_text()
b=Path('BUILD_ON_NAS.sh').read_text()
assert version_code_matches(g) and version_name_matches(g)
assert 'private void updateEditorSubtitle()' in s and 'v2.36-LayeredCell-RC1.apk' in b
assert 'DOCK_COMPACT_DP=250' in s and 'DOCK_MAX_PERCENT=38' in s
assert 'workspaceDockDp[activeWorkspace]' in s and 'Math.min(340,' in s
assert 'syncCanvasDockSpace(lp.height)' in s
assert 'int reserved=dp(50)+Math.max(dp(DOCK_COLLAPSED_DP),height);' in s
assert 'private void compactSliders(LinearLayout group,String key)' in s
assert 'sliders.get(j).setVisibility(active?View.VISIBLE:View.GONE)' in s
assert 'selectedSliderPills.put(key,names.get(selected))' in s
assert 'compactSliders(panels.get(i),key+":"+labels.get(i))' in s
assert 'sheetScroll.setVisibility(View.VISIBLE)' in s
assert 'if(dockAnimator!=null){dockAnimator.cancel()' in s
assert 'getRawY()<sheetTouchY-dp(12)' in s
assert 'new SeekBar(this)' in s and 'requestRender();' in s
assert 'VinylEngine.generate(' in s
print('PASS: short canvas-safe dock, one parameter slider, stable render listeners, touch and persistence')

#!/usr/bin/env python3
from pathlib import Path
src=Path('app/src/main/java/com/halftonestudio')
s=(src/'StudioActivity.java').read_text()
th=(src/'StudioProjectThumbnails.java').read_text()
p=(src/'BoundedTonalPreview.java').read_text()
assert 'BoundedTonalPreview.compose(' in s
assert 'Bitmap.createScaledBitmap(raw,' not in s[s.index('private Bitmap renderTonalPreview('):s.index('private Bitmap renderTonalPreview(')+1500]
assert 'gesturePreview' not in s and 'scheduleGesturePreview(' not in s
assert 'new ArrayBlockingQueue<Runnable>(32)' in th
assert 'ThreadPoolExecutor.DiscardOldestPolicy()' in th
assert 'options.inPreferredConfig=Bitmap.Config.RGB_565' in th
assert 'worker.getQueue().clear()' in th
assert 'Thread.currentThread().isInterrupted()' in p
assert 'versionCode ' in Path('app/build.gradle').read_text()
assert 'android.useAndroidX=true' in Path('gradle.properties').read_text()
assert 'Dot-to-Dot' not in s and 'dot-to-dot' not in s.lower()
print('PASS RC57 preview bounding, thumbnail queue and cleanup integration checks')

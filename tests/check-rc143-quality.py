#!/usr/bin/env python3
from pathlib import Path
import re,xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1];j=r/'app/src/main/java/com/halftonestudio'
a=(j/'StudioActivity.java').read_text()
assert 'try{draw(offscreen);}' in a and 'onDraw(offscreen)' not in a
for p in j.glob('*.java'):
 s=p.read_text()
 assert not re.search(r'setTypeface\(null,\s*[01]\)',s),p
 assert not re.search(r'Typeface.create\([^;\n]*,\s*[01]\)',s),p
 assert 'Gravity.RIGHT' not in s,p
 assert not re.search(r'String.format\("',s),p
crop=(j/'PhotoCropEditor.java').read_text().split('@Override protected void onDraw',1)[1].split('@Override public boolean onTouchEvent',1)[0]
assert 'new RectF(' not in crop and 'new Path(' not in crop
samples=a.split('private final class PatternSample',1)[1].split('private static final class CanvasGeometry',1)[0]
body=samples.split('@Override protected void onDraw',1)[1]
assert 'new Path(' not in body
assert 'v.performClick();' in a.split('fullScreenHandle.setOnTouchListener',1)[1].split('status=text',1)[0]
assert 'handle.setOnClickListener(v->toggleSheet())' in a
assert 'else v.performClick();' in a.split('private void attachSheetDrag',1)[1].split('private void setTool',1)[0]
assert 'androidx.annotation:annotation:1.9.1' in (r/'app/build.gradle').read_text()
build_script=(r/'BUILD_ON_NAS.sh').read_text()
assert 'lintDebug' in build_script and 'assembleDebug' in build_script and 'compileDebugJavaWithJavac' in build_script
xml=ET.parse(r/'app/src/main/res/values/strings.xml')
names=[s.attrib['name'] for s in xml.findall('string')]
assert len(names)==len(set(names))
assert len([n for n in names if n.startswith('quality_')])==27
assert (r/'app/src/main/res/mipmap-anydpi/ic_launcher.xml').is_file()
assert not (r/'app/src/main/res/mipmap-anydpi-v26').exists()
print('PASS RC143: drawing lifecycle, font constants, reusable draw scratch, click dispatch, locale/RTL/resources, annotation dependency and lint build gate')

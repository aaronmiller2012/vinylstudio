#!/usr/bin/env python3
"""Strong static gate: all 76 exact keys, all current chooser routes and tessellation selector."""
import csv,re
from pathlib import Path
r=Path(__file__).resolve().parents[1]
reg=(r/'app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java').read_text()
prev=(r/'app/src/main/java/com/halftonestudio/StudioStylePreview.java').read_text()
ui=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
rows=list(csv.DictReader((r/'tests/RC133-THUMBNAILS.csv').open()))
assert len(rows)==76,len(rows)
keys=[row['thumbnail_key'] for row in rows]
assert len(set(keys))==76
for k in keys[:54]:
    assert '"'+k+'"' in reg,k
assert 'TOP_LEVEL_COUNT = 54, TESSELLATION_COUNT = 22' in reg
assert '"tess.variant."+' in reg
assert 'if(!StudioThumbnailRegistry.isKnown(key))' in prev
assert 'TessellationMotifEngine.tiles(tessellation,100f,60f,19f)' in prev
assert 'if(tessellation>=0)drawTiling(output,tessellation)' in prev
for family,count in [('mosaic',13),('motif',15),('lines',8),('single',7),('art',11)]:
    start=prev.index('private void '+family+'(')
    end=prev.find('private void ',start+1)
    if end < 0:end=prev.index('@Override',start+1)
    body=prev[start:end]
    for i in range(count):
        assert (re.search(r'case\s+'+str(i)+r'\s*:',body) or
                (family=='motif' and i==14 and 'if(type==14)' in body) or
                (family=='single' and i==0 and 'if(type==0)' in body)),f'{family} {i} missing'
# Every picker path must supply exact keys independent of display name.
for route in ['StudioThumbnailRegistry.newStyle(id)', 'StudioThumbnailRegistry.shape(id)',
              'StudioThumbnailRegistry.line(id)', 'StudioThumbnailRegistry.single(line)']:
    assert route in ui,route
assert 'private void showTessellationLayoutPicker()' in ui
assert 'StudioThumbnailRegistry.tessellationKey(i)' in ui
assert 'selectTessellationLayout(pattern);' in ui
assert 'drawTiling(output,tessellation)' in prev
assert 'kind.contains(' not in prev
print('PASS RC133/RC142 54 exact picker keys and 22 Tessellation variant previews')

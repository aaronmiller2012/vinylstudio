#!/usr/bin/env python3
"""Release contract: new style category, exact 76 thumbnail identities, and pure category palettes."""
from pathlib import Path
import csv,json,re
root=Path(__file__).resolve().parents[1]
code=root/'app/src/main/java/com/halftonestudio'
registry=(code/'StudioThumbnailRegistry.java').read_text()
preview=(code/'StudioStylePreview.java').read_text()
ui=(code/'StudioActivity.java').read_text()
palette=(code/'StudioThumbnailPalette.java').read_text()
engine=(code/'VinylStyleSuite.java').read_text()
rows=list(csv.DictReader((root/'tests/RC133-THUMBNAILS.csv').open()))
meta=json.loads((root/'tests/release-info.json').read_text())
assert len(rows)==76 and len({r['thumbnail_key'] for r in rows})==76
counts={c:sum(r['category']==c for r in rows) for c in set(r['category'] for r in rows)}
assert counts=={'Multilayer':13,'Shapes':15,'Lines':8,'Single Line':7,'Art techniques':11,'Tessellation variant':22},counts
prismatic=[r for r in rows if r['selector']=='newstyle:26']
assert len(prismatic)==1 and prismatic[0]['category']=='Multilayer'
assert prismatic[0]['thumbnail_key']=='multilayer.prismatic_diagonal'
assert '"art.prismatic_diagonal"' not in registry
assert '"multilayer.prismatic_diagonal"' in registry
assert 'case VinylStyleSuite.PRISMATIC_DIAGONAL:return MULTILAYER[12];' in registry
assert 'addPickerChoice(out,multi,"Prismatic Diagonal Portrait"' in ui
assert 'addPickerChoice(out,shapes,"Prismatic Diagonal Portrait"' not in ui
assert 'PRISMATIC_DIAGONAL=26' in engine, 'Do not renumber persisted project style IDs'
assert 'case 12: // prismatic diagonal:' in preview
assert 'case 11: // sampler of two compatible repeating geometries' in preview
assert 'case 10: // torn-paper collage:' in preview
assert 'StudioThumbnailPalette.colors(key)' in preview
assert 'StudioThumbnailPalette.background(key)' in preview
assert 'StudioThumbnailPalette.outline(key)' in preview
for token in ('CYAN=','MAGENTA=','YELLOW=','BLACK=','WHITE=','SHAPE_ORDERS','private static final int[][] MULTI',
              'private static final int[][] LINES','private static final int[][] ART','tessellationVariant(key)',
              'return WHITE;','return new int[]{BLACK,BLACK,BLACK,BLACK,BLACK}'):
    assert token in palette,token
assert meta=={'version':'2.55.16','versionCode':428,
    'versionName':'2.55.16-thumbnail-refresh-rc151',
    'apk':'VinylHalftoneStudio-v2.55.16-ThumbnailRefresh-RC151.apk',
    'gate':'tests/run-rc151-thumbnails-gate.sh'}
assert meta['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
assert meta['gate'] in (root/'BUILD_ON_NAS.sh').read_text()
for script in ['PHONE_BUILD_AND_RECEIVE.sh','PHONE_RECEIVE_ONLY.sh','VERIFY_PACKAGE.sh']:
    text=(root/'transfer'/script).read_text()
    assert 'RC151' in text
    assert 'RC150' not in text
print('PASS RC151 thumbnail identity, migration, picker category, style ID, and release metadata')

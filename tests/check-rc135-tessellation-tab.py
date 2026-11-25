#!/usr/bin/env python3
"""RC135 picker regression: 22 tessellation variants appear directly in their own tab."""
from pathlib import Path
import re, json, csv
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
meta=json.loads((root/'tests/release-info.json').read_text())
assert meta['versionCode']>=413
assert meta['versionName'].startswith('2.55.')
assert 'final String[] groups={"All","Multilayer","Tessellation","Shapes","Lines","Single Line","Art techniques"};' in s
start=s.index('private ArrayList<PickerChoice> collectStylePickerChoices(')
end=s.index('private void showTessellationLayoutPicker()',start)
collector=s[start:end]
assert '"Tessellation", shapes="Shapes"' in collector
assert re.search(r'for\(int i=0;i<TessellationMotifEngine\.PATTERNS\.length;i\+\+\)',collector)
assert 'StudioThumbnailRegistry.tessellationKey(i)' in collector
assert 'selectTessellationLayout(pattern);' in collector
assert 'newStyleId==VinylStyleSuite.TESSELLATION_MOTIF&&newStyleVariant==i' in collector
assert 'VinylStyleSuite.TESSELLATION_MOTIF})' not in collector, 'Old Multilayer Tessellation tile still present'
assert 'showCompactStylePicker("Tessellation");' in s, 'In-style variant control must open the same tab'
assert 'activateNewStyle(VinylStyleSuite.TESSELLATION_MOTIF,pattern);' in s
assert 'newStyleVariant=NewStyleVariantPolicy.restore(id,requestedVariant);' in s
select=s[s.index('private void selectTessellationLayout('):s.index('private static String newStyleVariantName(')]
assert 'boolean changed=newStyleVariant!=pattern;' in select and 'if(changed)requestAlternate();' in select, 'Same-pattern selection should not regenerate'
assert 'checkpoint();' in select and 'newStyleVariantMemory[VinylStyleSuite.TESSELLATION_MOTIF]=pattern;' in select
assert 'setToolInternal(1);expandToolSheet();' in select
assert 'rememberNewStylePalette();' in s, 'Palette memory must persist on switching styles'
# Source registry and stable engine style IDs are unchanged; only the navigation is new.
registry=(root/'app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java').read_text()
assert 'TOP_LEVEL_COUNT = 54, TESSELLATION_COUNT = 22' in registry
assert 'TESSELLATION_MOTIF=10' in (root/'app/src/main/java/com/halftonestudio/VinylStyleSuite.java').read_text()
rows=list(csv.DictReader((root/'tests/RC133-THUMBNAILS.csv').open()))
assert len(rows)==76 and len([r for r in rows if r['thumbnail_key'].startswith('tess.variant.')])==22
print('PASS RC135 Tessellation tab location, 22 direct layouts, exact thumbnails, stable IDs, no duplicate regeneration')

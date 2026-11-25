#!/usr/bin/env python3
"""Prevent UI view-parent crashes in the automatic Snip L1 layer and palette indexing."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
start=s.index('    private void showAutomaticSilhouette(')
end=s.index('    private int[] standardLayerCutCounts=',start)
seg=s[start:end]
assert 'LinearLayout card=group(host);' in seg
assert 'host.addView(card)' not in seg, 'Android View cannot be parented twice'
assert 'private LinearLayout group(){return group(sheetContent);}' in s
assert 'parent.addView(b,p);return b;' in s
assert s.count('host.addView(card);')==0
assert 'final boolean standardLayer=!cells&&!tess&&!tonal;' in s
assert 'final boolean isLegacyBase=standardLayer&&i<settings.layers.size()&&settings.layers.get(i).silhouetteBase;' in s
assert 'String.format(Locale.US,"%,d %s",cutCounts[i],settings.layers.get(i).silhouetteBase?' not in s, 'specialty cut counts must not index standard palette'
# Keep all Layer page controls present rather than swallowing exceptions or hiding the panel.
assert 'showAutomaticSilhouette(overview);' in s and 'showAutomaticSilhouette(g);' in s
assert 'action(row,"Edit",this::changeAutoSilhouetteColor)' in seg
print('PASS RC75 Layers crash fix: single-parent L1 view, safe specialty cut counts, full controls preserved')

#!/usr/bin/env python3
"""Static integration checks supplement JVM geometry tests; no Android build claim."""
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
st=Path('app/src/main/java/com/halftonestudio/VinylStyleSuite.java').read_text()
for code in ('POP_PANELS=46','POSTER_STENCIL=47','isPopPoster(c.style)','PopPosterEngine.generate','organicCutGeometry'):
    assert code in st, f'Unwired style logic: {code}'
for code in ('popPosterOptions()', 'popPosterControls(host)', 'newStyleColorsPanel()',
             'newStyleLayersPanel()', 'PopPosterTextMask::create', 'popPanelsSettings',
             'posterStencilSettings', 'loadPopPosterSettings', 'savePopPosterSettings'):
    assert code in s, f'Missing UI or project persistence: {code}'
assert 'slot(newStyleId' in s and 'exportNewStyle' in s
assert 'addPickerChoice(out,arts,"Pop Panels"' not in s
assert 'addPickerChoice(out,arts,"Poster Stencil"' in s
assert 'if(VinylStyleSuite.isPopPoster(newStyleId)){popPosterColorsPanel();return;}' not in s
assert 'if(VinylStyleSuite.isPopPoster(newStyleId)){popPosterLayersPanel();return;}' not in s
assert 'StyleLayerOperations.bySlot(result,config.style,config.variant,config.colorCount)' in s
assert 'case POP_PANELS:return true' in st
assert 'POSTER_STENCIL)?9:8' in st
assert 'newStyleId==VinylStyleSuite.POSTER_STENCIL)?9:8' in s
assert 'posterUsesUnifiedPalette' in s
assert 'PopPosterEngine.posterPalette(posterOptions.posterPalette,9)' in s
assert 'return MULTILAYER[29]' in Path('app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java').read_text()
assert 'return MULTILAYER[30]' in Path('app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java').read_text()
# Regression guard: retired styles shrink the top-level registry by design.
# Validate the real registered keys, and ensure the count matches their
# declared cardinality instead of freezing the pre-retirement total.
import re
registry = Path('app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java').read_text()
arrays = {}
for family in ('MULTILAYER', 'SHAPES', 'LINES', 'SINGLE', 'ART'):
    match = re.search(r'\b' + family + r'\s*=\s*\{(.*?)\};', registry, re.S)
    assert match, f'Missing {family} thumbnail registry'
    arrays[family] = re.findall(r'"([^"]+)"', match.group(1))
all_keys = [key for family in arrays.values() for key in family]
assert len(set(all_keys)) == len(all_keys), 'Duplicate top-level thumbnail identity'
counts = re.search(r'TOP_LEVEL_COUNT\s*=\s*(\d+)\s*,\s*TESSELLATION_COUNT\s*=\s*(\d+)', registry)
assert counts and int(counts.group(1)) == len(all_keys), 'Thumbnail registry declaration does not match entries'
assert int(counts.group(2)) > 0, 'Missing tessellation thumbnail range'
assert arrays['MULTILAYER'][29:31] == ['multilayer.pop_panels', 'multilayer.poster_stencil'], 'Poster identities changed'
assert 'multilayer.painterly_cell_weave' not in all_keys and 'art.ridges' not in all_keys, 'Retired thumbnail restored'
assert 'return MULTILAYER[29]' in registry and 'return MULTILAYER[30]' in registry, 'Poster mappings changed'
assert 'PopPosterEngine' in Path('app/src/main/java/com/halftonestudio/PopPosterTextMask.java').read_text() or 'class PopPosterTextMask' in Path('app/src/main/java/com/halftonestudio/PopPosterTextMask.java').read_text()
print('PASS retired Pop, shared Poster nine-slot colors/layers UI, saved state and export')

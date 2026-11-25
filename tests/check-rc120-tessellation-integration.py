#!/usr/bin/env python3
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
src=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
suite=(root/'app/src/main/java/com/halftonestudio/VinylStyleSuite.java').read_text()
engine=(root/'app/src/main/java/com/halftonestudio/TessellationMotifEngine.java').read_text()
meta=json.loads((root/'tests/release-info.json').read_text())
schema=(root/'app/src/main/java/com/halftonestudio/ProjectSettingsSchema.java').read_text()
assert 'TESSELLATION_MOTIF=10' in suite
assert '"(retired style 8)"' in suite
assert 'case TESSELLATION_MOTIF:{' in suite
assert 'VinylStyleSuite.TESSELLATION_MOTIF' in src
assert 'config.motifFamily=newStyleTessMotif;' not in src
assert 'action(host,"Motifs: "' not in src
assert 'newStyleTessMotif' in src and 'out.put("newStyleTessMotif"' in src
assert (('static final int CURRENT=28;' in schema) or ('static final int CURRENT=29;' in schema)) and 'version<26' in schema
assert 'case VinylStyleSuite.TESSELLATION_MOTIF' in (root/'app/src/main/java/com/halftonestudio/NewStyleVariantPolicy.java').read_text()
assert 'style == VinylStyleSuite.TESSELLATION_MOTIF' in (root/'app/src/main/java/com/halftonestudio/NewStyleColorSlots.java').read_text()
assert 'private static float[] clipTo(' in engine and 'private static float[] halfPlane(' in engine
assert 'PhotoCellImportance.create' not in engine and 'MaskedFacetClipper.intersect' in engine
assert len((root/'tests/golden/geometry-signatures.tsv').read_text().splitlines())==229
assert meta['versionCode']>=406 and meta['gate'].startswith('tests/run-')
assert meta['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
assert 'tests/run-rc125-palette-offset-gate.sh' in (root/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()
print('PASS RC121 Tessellation style, variants, color slots, persistence, schema and build integration')

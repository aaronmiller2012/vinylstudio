#!/usr/bin/env python3
"""Verify the staged export and all four production branches, not just standalone DXF strings."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
profile=(root/'app/src/main/java/com/halftonestudio/OutputDeviceProfile.java').read_text()
zip_stage=(root/'app/src/main/java/com/halftonestudio/StagedCutZip.java').read_text()
assert source.count('new CutSheetZipWriter(zip,targetForThisExport,')==3 and source.count('new CutSheetZipWriter(zip,target,')==1, 'Missing one of four cut export writers after pre-save staging'
assert source.count('cuts.finish();writeOutputHandoff(zip,targetForThisExport);')==3 and source.count('cuts.finish();writeOutputHandoff(zip,target);')==1, 'Missing final report/handoff in one of four export routes'
assert source.count('cuts.put(')>=15, 'Not all existing physical cut sheets use format writer'
assert 'cuts.manifest(standardLayerManifest(result,base))' in source
assert 'cuts.manifest(tonalLayerManifest(layers))' in source
assert 'cuts.manifest(csv.toString())' in source
assert 'cuts.manifest(manifest.toString())' in source
assert 'cuts.fileName(autoBaseFilename(base))' in source
# A grouped cross-color SVG was intentionally retired: it could invite users to
# combine physically separate vinyl layers into one cut sheet. This must remain
# format-neutral (SVG and DXF), not just disabled for Silhouette Basic DXF.
assert 'putZip(zip,"grouped-svg-not-included.txt",' in source, (
    'Missing explicit explanation for omitted cross-color grouped cut sheet')
assert 'A merged cross-color SVG is omitted for cut safety.' in source, (
    'Missing safety rationale for using numbered per-color sheets')
# Check executable writer calls, not mere references to the old filename in comments.
assert not re.search(r'\b(?:putZip|cuts\.put)\s*\(\s*(?:zip\s*,\s*)?"[^"]*(?:all-swatches-grouped|grouped-cut)\.(?:svg|dxf)"', source), (
    'Unsafe grouped cross-color cut sheet must not be exported')
assert 'if(svgs==0&&dxfs==0)' in zip_stage, 'Staged ZIP rejects DXF-only cut sheets'
assert 'fields[1].endsWith(".dxf")' in zip_stage, 'Archive manifests not checked for DXF'
assert 'GRAPHTEC_STUDIO=6' in profile and 'SISER_LEONARDO=7' in profile
assert 'SILHOUETTE_BASIC=5' in profile, 'Preference IDs must be append-only'
assert 'getInt("outputDeviceId",OutputDeviceProfile.CRICUT)' in source
assert 'stage.verify();' in source and 'publishPreparedStandardCutZip(uri)' in source, 'Standard export must be prepared before SAF publication'
print('PASS RC172 all four export routes (three direct and one prepared), manifests, archive integrity, unchanged IDs')

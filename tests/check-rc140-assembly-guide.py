#!/usr/bin/env python3
from pathlib import Path
import json

root=Path(__file__).resolve().parents[1]
activity=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
art=(root/'app/src/main/java/com/halftonestudio/ArtworkDocumentExport.java').read_text()
guide=(root/'app/src/main/java/com/halftonestudio/AssemblyGuideDocument.java').read_text()
plan=(root/'app/src/main/java/com/halftonestudio/AssemblyGuidePlan.java').read_text()
build=(root/'app/build.gradle').read_text()
meta=json.loads((root/'tests/release-info.json').read_text())

# Project Library entry point keeps the established save/discard/cancel switch guard.
assert 'TextView newProject=text("New Project"' in activity
assert 'requestProjectSwitch(()->{' in activity
assert 'closeCurrentProject();\n            pick();' in activity
assert 'TextView save=text("Save Current"' in activity
assert 'TextView importZip=text("Import ZIP"' in activity

# The guide is a real Storage Access Framework PDF export, not the RC139 preview scaffold.
assert 'Export assembly guide PDF' in activity
assert 'EXPORT_ASSEMBLY_GUIDE=114' in activity
assert 'intent.setType("application/pdf")' in activity
assert 'VinylStudio-assembly-guide-' in activity
assert 'AssemblyGuideDocument.writePdf(guide,output)' in activity
assert 'PDF export is staged but remains disabled' not in activity
assert 'Preview assembly guide' not in activity

# Every active renderer has a vector guide route and isolated physical pages.
for required in ('standardLayer(', 'newStyleLayer(', 'specialtyLayer(', 'stippleLayer(', 'linePath('):
    assert required in art, required
for required in ('standardFinalReady()', 'newStyleActive()', 'specialtyVectorCache',
                 'stippleVectorCache', 'mazeResult', 'continuousPreview'):
    assert required in activity, required
assert 'PdfDocument' in guide and 'drawCover(' in guide and 'drawLayerPage(' in guide
assert 'layerMappingComplete' in plan and 'PEN_OR_ENGRAVE' in plan

assert meta['versionCode']>=417 and meta['versionName'].startswith('2.55.')
assert meta['gate'] in ('tests/run-rc140-assembly-guide-gate.sh','tests/run-rc141-fifteen-styles-gate.sh',
                        'tests/run-rc142-style-cleanup-gate.sh','tests/run-rc143-quality-gate.sh','tests/run-rc144-registration-gate.sh','tests/run-rc145-registration-stability-gate.sh','tests/run-rc146-reliability-gate.sh','tests/run-rc147-registration-viewport-gate.sh','tests/run-rc148-registration-safety-gate.sh','tests/run-rc149-prismatic-gate.sh','tests/run-rc150-prismatic-tone-gate.sh','tests/run-rc151-thumbnails-gate.sh')
assert f"versionCode {meta['versionCode']}" in build and f"versionName '{meta['versionName']}'" in build
if meta['gate']=='tests/run-rc141-fifteen-styles-gate.sh':
    assert 'bash tests/run-rc140-assembly-guide-gate.sh' in (root/meta['gate']).read_text()
if meta['gate']=='tests/run-rc142-style-cleanup-gate.sh':
    assert 'bash tests/run-rc140-assembly-guide-gate.sh' in (root/meta['gate']).read_text()
print('PASS RC140 inherited New Project, full assembly-guide PDF, and style coverage')

if meta['gate']=='tests/run-rc143-quality-gate.sh':
    assert 'bash tests/run-rc142-style-cleanup-gate.sh' in (root/meta['gate']).read_text()

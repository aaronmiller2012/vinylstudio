#!/usr/bin/env python3
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
app=root/'app/src/main/java/com/halftonestudio'
activity=(app/'StudioActivity.java').read_text()
document=(app/'ArtworkDocumentExport.java').read_text()
layout=(app/'ArtworkExportLayout.java').read_text()
meta=json.loads((root/'tests/release-info.json').read_text())
assert meta['versionCode']>=406 and meta['gate'].startswith('tests/run-')
assert meta['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
assert 'EXPORT_PNG=112, EXPORT_PDF=113' in activity
assert 'Export composite PNG image' in activity and 'Export composite PDF document' in activity
assert 'Export separate SVG layers as ZIP' in activity
assert 'intent.setType(pdf?"application/pdf":"image/png")' in activity
assert 'Intent.ACTION_CREATE_DOCUMENT' in activity
assert 'req==EXPORT_PNG||req==EXPORT_PDF' in activity
assert 'Release sliders and finish the final render' in activity
assert 'generation!=pendingDocumentGeneration' in activity
assert 'ArtworkDocumentExport.writePdf(artwork,output)' in activity
assert 'ArtworkDocumentExport.writePng(artwork,output)' in activity
assert 'PdfDocument' in document and 'doc.finishPage(pdf)' in document
assert 'Bitmap.CompressFormat.PNG' in document
assert 'vinyl' not in layout.lower() or 'png' in layout.lower()
assert 'PNG_MAX_PIXELS=12_000_000' in layout
print('PASS RC110 document UI/MIME/SAF, generation isolation, native PNG/PDF, SVG retention')

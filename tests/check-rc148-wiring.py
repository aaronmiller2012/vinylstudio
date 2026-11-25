#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
marks=(r/'app/src/main/java/com/halftonestudio/RegistrationCutGeometry.java').read_text()
guide=(r/'app/src/main/java/com/halftonestudio/AssemblyGuideDocument.java').read_text()
plan=(r/'app/src/main/java/com/halftonestudio/AssemblyGuidePlan.java').read_text()
art=(r/'app/src/main/java/com/halftonestudio/ArtworkDocumentExport.java').read_text()
for text in ('activeMarkReport()', 'Reference PDF without marks', 'startAssemblyGuidePicker(true)',
             'referenceOnly(assemblyReferenceOnly)', 'commitMarkReport()', 'committedMarkReport.invalid(i)',
             'RegistrationCutGeometry.Report report=activeMarkReport()',
             'RegistrationCutGeometry.style(newStyleResult',
             'RegistrationCutGeometry.standard(latest)',
             'RegistrationCutGeometry.cells(layeredResult)',
             'RegistrationCutGeometry.tessellation(tessellationResult)'):
    assert text in s,text
assert 'RegistrationMarks.requireOutsideArtwork(marks,width,height' not in s
assert '.referenceOnly(assemblyReferenceOnly)' in s
assert 'includeMarks&&result.settings.registration' in art
assert 'guide.plan.referenceOnly' in guide
assert 'REFERENCE ONLY:' in plan
assert 'Illustration not to scale' in guide
assert 'if(guide.plan.referenceOnly)' in guide
for name,needle in [('NewStyleExport.java','RegistrationCutGeometry.style(result'),
 ('LayeredCellPortraitEngine.java','RegistrationCutGeometry.cells(this)'),
 ('TessellationEngine.java','RegistrationCutGeometry.tessellation(this)'),
 ('MultilayerStencilEngine.java','registrationReport()'),
 ('VinylEngine.java','RegistrationCutGeometry.standard(r).firstIssue()')]:
    assert needle in (r/'app/src/main/java/com/halftonestudio'/name).read_text(),name
assert 'checkFilled' not in marks or 'hitsFilled' in marks
print('PASS RC148 source wiring: production preflight, geometry-specific mark checks, individual mark feedback, nonproduction PDF')

#!/usr/bin/env python3
"""Enforce safety and atomic-save requirements for every style export path."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
writer=(root/'app/src/main/java/com/halftonestudio/CutSheetZipWriter.java').read_text()
compat=(root/'app/src/main/java/com/halftonestudio/CutSvgCompatibility.java').read_text()
for call in ('exportNewStyle(null)','exportSilhouetteOnly(null,currentUnderlay())','exportAlternate(null)'):
    assert call in s, 'Specialty route bypasses prepare-before-save: '+call
assert 'offerPreparedSpecialtyZip' in s and 'publishPreparedSpecialtyZip' in s
assert s.count('stage.verify();')>=4, 'ZIP verification absent on an export route'
assert 'publishStagedCutZip(ready,uri)' in s
assert 'discardPreparedSpecialtyCutZip()' in s
assert 'if(req==ALTERNATE_EXPORT)discardPreparedSpecialtyCutZip();' in s
assert 'if(!reviewApproved && narrowFindingCount()>0)' in writer
assert 'NOT-CUT-QUALIFIED.txt' in writer
assert 'CricutMatPreflight.requireFits' in writer
assert 'CutSvgCompatibility.verify(svg,outputDeviceId)' in writer
assert 'deviceId==OutputDeviceProfile.CRICUT&&count>MAX_ELEMENTS' in compat
assert 'cuts.noteSourceFindings(cutReview.requiresConfirmation());' in s
assert 'preparingSpecialtyCutZip=false;' in s[s.index('if(cutReview.requiresConfirmation()&&!acknowledgedCutSafety)'):s.index('stage=new StagedCutZip(getCacheDir());',s.index('if(cutReview.requiresConfirmation()&&!acknowledgedCutSafety)'))]
for f in ('V3AllStyleExportMatrixTest.java','V3ExportProfilesTest.java'):
    assert (root/'tests'/f).is_file()
print('PASS alternate-style pre-save, incomplete warnings, per-cutter path and mat gates')

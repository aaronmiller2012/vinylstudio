#!/usr/bin/env python3
"""Guard both physical registration alignment and retired-style picker cleanup."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
d=root/'app/src/main/java/com/halftonestudio'
ui=(d/'StudioActivity.java').read_text()
reg=(d/'RegistrationMarks.java').read_text()
info=json.loads((root/'tests/release-info.json').read_text())
assert info['versionCode']>=346 and (root/info['gate']).is_file()
assert info['versionName'] in (root/'app/build.gradle').read_text()
assert info['gate'] in (root/'BUILD_ON_NAS.sh').read_text()
assert 'final float[][] markPositions=RegistrationMarks.copyPositions(settings.marks)' in ui
assert 'RegistrationMarks.copyPositions(markPositions)' in ui
# V3 stages the immutable export snapshot as `cut`, rather than the old
# renderer-local `result`; both use the same captured registration coordinates.
# ADR-0001: the export also passes circle crop so marks match the preview and Snip base;
# behaviour is covered by tests/V3ExportSheetContractTest.java.
assert 'NewStyleExport.layerSvg(cut,i,marks,markStyle,markSize,clearance,manualMarks,minCut,circleCrop)' in ui
assert 'if(tool==5&&settings.registration)' in ui
assert 'float[][] touchMarks=displayedMarks(gw,gh)' in ui
assert 'requireOutsideArtwork' in reg and 'Math.max(r*.18f,minimumCutMm*.5f)' in reg
for name in ('TessellationEngine.java','LayeredCellPortraitEngine.java','MultilayerStencilEngine.java'):
    source=(d/name).read_text()
    # Shared registration layout: SheetFrame.of (ADR-0001) wraps RegistrationMarks.artworkLayout.
    assert ('RegistrationMarks.layout(' in source or 'SheetFrame.of(' in source) and ('RegistrationMarks.requireOutsideArtwork(' in source or 'RegistrationCutGeometry.' in source)
    assert 'RegistrationMarks.copyPositions(marks)' in source
multilayer=ui[ui.index('private void showMultilayerPickerDialog()'):ui.index('private void stylePickerBack(')]
assert '"Word Mosaic"' not in multilayer and '"Word Knockout"' not in multilayer
assert 'activateTextPortrait(1)' not in multilayer and 'activateTextPortrait(2)' not in multilayer
assert (d/'WordKnockoutEngine.java').is_file()
assert (d/'WordPortraitEngine.java').is_file(), 'saved legacy project code must survive'
print('PASS RC68 physical mark selection, specialty parity, safe features and no new Word style entries')

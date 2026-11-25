from pathlib import Path
base=Path('app/src/main/java/com/halftonestudio')
ui=(base/'StudioActivity.java').read_text()
for fragment in ['NativeOriginalPhoto.stage(getCacheDir()', 'NativeOriginalPhoto.commit(getFilesDir()',
                 'ownership.own(staged::close)', 'toggleSpecialtyCutRiskMarkers()',
                 'drawSpecialtyRiskOverlay(cv,selected,artW)', 'drawSpecialtyRiskOverlay(cv,tessSelectedLayer,artW)',
                 'specialtyRiskSource!=currentSpecialtyRiskSource()', 'if(!specialtyFinalReady())']:
    assert fragment in ui, fragment
assert 'if(source==specialtyRiskSource)' in ui
for f in ['SourceTilePlan.java','NativeOriginalPhoto.java','SpecialtyCutWarningLocator.java']:
    assert (base/f).exists(),f
# No new adjustment sliders or tools; no rendering algorithm swaps.
assert 'private void requestExport()' in ui
assert 'narrowCutLocations(minimum,80)' in ui
assert 'SpecialtyCutWarningLocator.locate(source,minimum,80)' in ui
print('PASS v2.54.2 UI wiring, old-generation guards, staged originals, unchanged export dispatcher')

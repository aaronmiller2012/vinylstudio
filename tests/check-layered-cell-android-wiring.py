from pathlib import Path
src=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
engine=Path('app/src/main/java/com/halftonestudio/TessellationEngine.java').read_text()
needs=[
'"Layered Cell Portrait"', 'layeredCellMode=false','layeredCellMode=true',
'layeredResult=cellReady','renderLayeredPreview','LayeredCellPortraitEngine.generate',
'LayeredCellExportSafety.validate','out.put("layeredCellMode",layeredCellMode)',
'json.optBoolean("layeredCellMode",false)','LayeredCellReleaseProfile.safeColumns',
'case 3:if(newStyleActive())newStyleLayersPanel();else unifiedLayersPanel();break;','layeredResult.svg(layer)',
'if(tessMode)mosaic=TessellationEngine.generate',
]
for phrase in needs:
    if phrase not in src:
        if phrase=='layeredResult.svg(layer)' and 'cellPortrait.svg(layer)' in src: continue
        raise AssertionError('Missing layered mode contract: '+phrase)
assert 'public static final int GRID_MOTIF=1' in engine or 'GRID_MOTIF=1' in engine
assert 'LayeredCellPortraitEngine' not in engine, 'Legacy mosaic altered'
assert '"layeredColumns"' in src and '"layeredMinFeatureMm"' in src
assert 'if(layeredCellMode){layeredControls(g);}' in src
assert 'if(cells||tess)colors[i]=tessColors[i];' in src and 'private void unifiedLayersPanel()' in src
print('PASS: new style selection and settings, legacy Grid Cell Portrait kept, preview/export/state wired')

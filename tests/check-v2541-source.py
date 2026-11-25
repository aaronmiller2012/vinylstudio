from pathlib import Path
root=Path('app/src/main/java/com/halftonestudio')
s=(root/'StudioActivity.java').read_text()
assert 'TiledImageLevels.apply(input,w,h,s)' in s
assert 'CutWarningLocator.locate(reference,80)' in s
assert 'cutRiskResult==r' in s
assert 'showCutRiskMarkers' in s
assert 'settings.pattern!=VinylEngine.BROKEN' in s
assert 'LineArtEngine.isLineStyle(settings.pattern)' in s
assert 'regionalPriority(x,y,base)' in (root/'LineArtEngine.java').read_text()
assert 'RegionalPortraitDetail.priority(cellMmX' in (root/'LayeredCellPortraitEngine.java').read_text()
print('PASS wiring: tiled tonal preparation, current-render markers, CMYK all line families, region allocation')

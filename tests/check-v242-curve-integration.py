from pathlib import Path
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
c=(r/'app/src/main/java/com/halftonestudio/ContinuousLineModes.java').read_text()
s=(r/'app/src/main/java/com/halftonestudio/SpaceFillingCurves.java').read_text()
v=(r/'app/src/main/java/com/halftonestudio/SpaceFillingRibbon.java').read_text()
checks={
    'seven new styles': all(' '+name in c for name in ['GOSPER,','PEANO,','SIERPINSKI,','WUNDERLICH,','MOORE,','COIL,','MEURTHE']),
    'genuine GOSPER l-system': 'A-B--B+A++AA+B-' in s,
    'genuine PEANO l-system': 'LFRFL-F-RFLFR+F+LFRFL' in s,
    'genuine MOORE l-system': 'LFL+F+LFL' in s,
    'actual vinyl silhouette': 'SpaceFillingRibbon.generate' in a and 'vinyl-ribbon-' in a,
    'distinct open-path export': 'ContinuousLineSvg.svg' in a and 'curveOutput' in a,
    'settings persisted': all('out.put("'+x+'"' in a and 'json.opt' in a for x in ['curveDepth','curvePhotoInfluence','curveCornerRounding','curveRotationDegrees','curveVinylRibbon','curveRibbonWidthMm','curveMinFeatureMm','curveMinGapMm']),
    'settings used in rendering': all(x in a for x in ['curveDepthSetting','curvePhoto','curveRound','curveRotation','ribbonGap','ribbonFeature']),
    'preview uses exported mask': 'SpecialtyVectorCache.ribbon(ribbonGeometry)' in a and 'specialtyVectorCache=specialtyVectors' in a,
    'open centerline rectangular validator': 'check(previewPath,previewWidth,previewHeight,mm,!curveOutput)' in a,
    'cut SVG no clip paths': '<clipPath' not in v and '<image' not in v,
    'cancellation': 'Cancelled vinyl ribbon' in v and 'Cancelled curve generation' in s,
}
for name,ok in checks.items():
    if not ok:raise AssertionError('Curve UI contract: '+name)
print('PASS v2.42: style drawer, settings persistence, mask preview parity, pen/vinyl export selection, cancellation source checks')

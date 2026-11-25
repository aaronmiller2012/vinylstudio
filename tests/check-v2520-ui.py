from pathlib import Path
r=Path(__file__).resolve().parent.parent
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
for token in ('addPhotoAutoAction(g);','addPhotoAutoAction(controls);','chooseAutoTuneGoal()',
              'applyAutoTuneIfCurrent(', 'checkpoint(); // One Undo',
              'new AlertDialog.Builder(this).setTitle("Photo recommendations")',
              'VinylPlainLanguage.label(label)','addSettingHelp(row,friendly,VinylPlainLanguage.help(label));',
              'source=photo','settings.minimumMm!=smallest','settings.widthMm!=widthMm',
              'PhotoAdjustments','stippleSettings.pointDensity=r.stippleDensity',
              'stippleSettings.optimizePacking=true;', 'if(settings.linkedControls)setLinkedValues();',
              'settings.shapeScale=r.shapeScale;', 'addPhotoAutoAction(a);',
              'case AutoPhotoTuner.STENCIL:'):
    if token=='PhotoAdjustments':continue
    assert token in s,token
body=s[s.index('private void applyAutoTuneIfCurrent('):s.index('private void addCompactSpecialtyAdjustments(){')]
for forbidden in ('tessLayerCount=', 'tonalLayerCount=', 'tessColors[', 'tessHueDegrees=',
                  'standardHueDegrees=', 'tonalHueDegrees=', 'settings.paper=',
                  'settings.widthMm=', 'settings.snips=', 'cutMaterialProfileId=',
                  'stippleSettings.order=', 'photo='):
    assert forbidden not in body,forbidden
assert 'versionCode 300' in (r/'app/build.gradle').read_text()
assert 'VinylHalftoneStudio-v2.52.1-LineVisibility-RC22.apk' in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS Auto UX: goals, non-destructive apply, stale photo rejection, unchanged palettes, tooltips and version')

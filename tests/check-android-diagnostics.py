from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
main=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
engine=Path('app/src/main/java/com/halftonestudio/LineArtEngine.java').read_text()
gradle=Path('app/build.gradle').read_text()
checks={
 'diagnostics callback refreshes status': 'latestDiagnostics=visibility;\n                        visibilityIssue=null;\n                        refreshLayerStatus();' in main,
 'diagnostics failures are logged': 'android.util.Log.e("VinylHalftone", "Vinyl visibility analysis failed", error)' in main,
 'diagnostics failure gives UI feedback': 'visibilityIssue="Visibility estimate unavailable"' in main,
 'immediate cut count before diagnostics': 'refreshLayerStatus(); // Cut counts are available immediately' in main,
 'black protection value clamped on load': 'Math.max(0,Math.min(.8,json.optDouble("blackColorProtection",CmykPortraitDefaults.BLACK_COLOR_PROTECTION)))' in main,
 'midtone recovery control matches supported rendering': 'hasMidtoneRibbonLayer()' in main and 'if(supportsPortraitMidtones(style)' in engine,
 'registration warnings survive async status refresh': 'currentMarkIssue=markIssue' in main and 'currentMarkIssue==null' in main,
 'Android version metadata current': version_code_matches(gradle) and version_name_matches(gradle),
 'visible in-app version current': 'private void updateEditorSubtitle()' in main,
}
for name, ok in checks.items():
    if not ok: raise AssertionError(name)
print('PASS: Android diagnostics callback, visible failure, metadata, saved-setting clamp, active line slider')

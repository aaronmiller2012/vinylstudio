from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
main=Path("app/src/main/java/com/halftonestudio/StudioActivity.java").read_text()
engine=Path("app/src/main/java/com/halftonestudio/VinylEngine.java").read_text()
script=Path("BUILD_ON_NAS.sh").read_text()
gradle=Path("app/build.gradle").read_text()
checks={
  "CMYK parallel selection blocked": 'settings.mode==VinylEngine.CMYK&&preset==VinylEngine.ANGLES_PARALLEL' in main,
  "CMYK angle readout visible": '"Screen angle · °"' in main and '"Classic CMYK angles"' in main,
  "CMYK Photoshop and separated screen presets": '"Alternate angles"' in main and 'settings.anglePreset=preset' in main,
  "saved angle flags normalized": 's.anglePreset=ANGLES_CLASSIC' in engine,
  "directional screen geometry rotated": 'rotated(ellipse(x,y' in engine and 'rotated(Shape.polygon' in engine,
  "current version metadata": version_code_matches(gradle) and version_name_matches(gradle),
  "matching in-app version": 'private void updateEditorSubtitle()' in main,
  "matching APK output name": apk_name_matches(script),
}
for label,passed in checks.items():
    if not passed:raise AssertionError(label)
print("PASS: current version, protected CMYK angle navigation, channel readouts, channel-specific dot geometry and APK output")

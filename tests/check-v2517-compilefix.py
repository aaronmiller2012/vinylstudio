from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
src=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
start=src.index('private void restoreArtworkModes(JSONObject json)')
end=src.index('private String snapshot()', start)
block=src[start:end]
# The fromJson local named s is not available to restoreArtworkModes.
code='\n'.join(line.split('//')[0] for line in block.splitlines())
assert not re.search(r'(?<![\w])s\s*\.',code), 'Out-of-scope s reference in restoreArtworkModes'
assert 'settings.layers.get(i).color' in code
assert 'settings.mode==VinylEngine.CMYK' in code
assert 'java.util.Arrays.fill(cmykAccentHueBaseColors,0)' in code
assert src.index('settings=fromJson(restored);restoreParkedVinylLayers(restored);restoreArtworkModes(restored)')>0
assert src.index('settings=fromJson(obj);restoreParkedVinylLayers(obj);restoreArtworkModes(obj)')>0
assert ('versionCode 296' in (root/'app/build.gradle').read_text() or 'versionCode 300' in (root/'app/build.gradle').read_text())
print('PASS palette restore scope: no undefined s, loaded settings used, legacy accents reset')

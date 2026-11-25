from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
segment=s[s.index('private void countLayers(int n)'):s.index('private void layerCountControl(')]
assert 'rebaseStandardHue()' not in segment, 'Hue offset must never reset on layer count change'
assert 'applyActivePresetColors()' not in segment, 'Selected and manually edited palette must not be reapplied'
assert 'standardHueDegrees' in segment and 'cmykAccentHueBaseColors' in segment
assert 'System.arraycopy(nextBases,0,standardHueBaseColors,0,8)' in segment
assert 'parkedVinylLayers.add(settings.layers.remove' in segment
assert 'parkedVinylLayers.remove(parkedVinylLayers.size()-1)' in segment
n=s[s.index('layerCountPicker(overview,count,n->'):s.index('if(!cells&&!tess&&!tonal&&settings.mode')]
assert 'refreshTessPaletteForCount()' not in n
assert 'setTessBasePalette(' not in n
assert 'applyTessHue(' not in n
assert 'invalidateStipplePhotoPaletteRequest()' in n
assert 'tessLayerCount=n;' in n
assert 'cmykAccentHueBaseColors' in s[s.index('private JSONObject toJson('):s.index('private VinylEngine.Settings fromJson')]
load=s[s.index('JSONArray stdBase=json.optJSONArray'):s.index('tonalHueDegrees=PaletteHueShift.clampDegrees(json.optInt',s.index('JSONArray stdBase=json.optJSONArray'))]
# restoreArtworkModes() operates on the *loaded* settings, not the local
# s variable of fromJson(), which is out of scope at this point.
assert 'settings.layers.get(i).color' in load
assert 'settings.mode==VinylEngine.CMYK' in load
import re
assert not re.search(r'(?<![\w])s\s*\.', '\n'.join(line.split('//')[0] for line in load.splitlines()))
assert 'cmykAccentHueBaseColors' in load
assert ('versionCode 296' in (r/'app/build.gradle').read_text() or 'versionCode 300' in (r/'app/build.gradle').read_text())
print('PASS count invariants: tessellation/stipple/spot/CMYK retain selected color and hue, saved-project restoration')

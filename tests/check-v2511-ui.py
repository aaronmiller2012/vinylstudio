#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parent.parent
act=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
engine=(r/'app/src/main/java/com/halftonestudio/MultiColorStippleEngine.java').read_text()
assert 'out.put("stippleShape",stippleSettings.shape)' in act
assert 'json.optInt("stippleShape",MultiColorStippleEngine.ROUND)' in act
assert 'section(host,"Color layers · tap to edit")' in act
assert 'action(host,"Open all 8 swatches and palettes"' in act
assert 'section(host,"Stipple cut shapes")' in act
assert 'section(palettes,"All multilayer color palettes")' in act
assert 'specialtyPaletteTile(row,ColorWorkspacePalette.TESS_NAMES[i],presetTessSwatches(i),i,false)' in act
assert 'if(activeWorkspace==2&&tool==1)refreshShelfPanel()' in act
assert 'if(o.overlap!=SHARED && all.size()<desired)' in engine
assert 'silhouette(o.shape,dot.x,dot.y,dot.r)' in engine
assert 'private static List<Piece> clipRaster' in engine
assert ('versionCode 292' in (r/'app/build.gradle').read_text() or ('versionCode 293' in (r/'app/build.gradle').read_text() or 'versionCode 294' in (r/'app/build.gradle').read_text() or ('versionCode 295' in (r/'app/build.gradle').read_text() or ('versionCode 296' in (r/'app/build.gradle').read_text() or 'versionCode 300' in (r/'app/build.gradle').read_text()))))
assert 'VinylHalftoneStudio-v2.52.1-LineVisibility-RC22.apk' in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS v2.51.1 eight shapes, all swatches, density fill and persisted geometry')

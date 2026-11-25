from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
cache=Path('app/src/main/java/com/halftonestudio/SpecialtyVectorCache.java').read_text()
required=[
 'specialtyVectorCache', 'SpecialtyVectorCache.tessellation(mosaic)',
 'SpecialtyVectorCache.tonal(tones)', 'SpecialtyVectorCache.layered(cellPortrait)',
 'SpecialtyVectorCache.ribbon(ribbonGeometry)', 'drawSpecialtyVectors(cv,vectors)',
 'drawContinuousVectors(cv)', 'drawStippleVectors(cv,cuts)',
 'cv.drawPath(path,paint)', 'cv.drawPath(cache.paths[li],paint)',
 'cv.drawPath(line,paint)', 'cv.drawPath(maze.wallsPath,paint)',
 'alternateReadyGeneration==alternateGeneration'
]
for x in required: assert x in s,x
assert 'MultilayerStencilEngine.appendUnionContours' in cache
assert 'r.cutPathData(i)' in cache and 'pathFromMLCZ' in cache
# No specialty raster generation for completed main display. Export thumbnails remain optional.
assert 'final Bitmap display=null;' in s
assert 'if(!draft) scheduleGesturePreview(vectorCache,id);' not in s
assert 'transforming&&cache.gesturePreview!=null' not in s
import json
release=json.loads(Path('tests/release-info.json').read_text())
assert release['gate'] in Path('app/build.gradle').read_text()
print('PASS UI: finished standard, stipple and all specialty styles use vector canvas paths')

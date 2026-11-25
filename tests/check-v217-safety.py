from pathlib import Path
p=Path(__file__).resolve().parents[1]
a=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
s=(p/'app/src/main/java/com/halftonestudio/SpecialtyCutSafety.java').read_text()
checks={
'shared prepared source': 'SpecialtyCutSafety.prepare(source,snips,circular)' in a,
'processed source reaches continuous generator':'generatePath(prepared,style,draft?Math.max(1,Math.round(density*.65f)):density,circular)' in a,
'processed source reaches layers':'MultilayerStencilEngine.generate(pixels,small.getWidth(),small.getHeight(),options)' in a,
'preview snapshot reused for SVG export':'ContinuousLineSvg.svg(previewPath,previewWidth,previewHeight,mm,lineStrokeMm)' in a,
'no export path regeneration':'ContinuousLineModes.generatePath(bitmap,style,density)' not in a,
'stale renders blocked':'alternateReadyGeneration!=alternateGeneration' in a,
'polygon snip guard':'Continuous path cannot honor polygon snips without breaks' in a,
'physical SVG path limit':'count(svg,"<path")>5000' in s,
'circle and polygon mask':'snips.allows(nx,ny)' in s and 'boolean allowed=!circle' in s,
'svg preflight':'SpecialtyCutSafety.validateLayerSvg(svg)' in a and 'SpecialtyCutSafety.validateSingleSvg(svg)' in a,
}
for k,v in checks.items():
 if not v:raise AssertionError(k)
print('PASS: V2.17 shared prepared geometry, immutable export snapshot, stale-result & cut-safety guards')

from pathlib import Path
r=Path(__file__).resolve().parents[1]
j=r/'app/src/main/java/com/halftonestudio'
s=(j/'PrismaticDiagonalPortraitEngine.java').read_text()
a=(j/'StudioActivity.java').read_text()
assert 'new BandField(f,o,cell,false,candidates,lab)' in s and 'new BandField(f,o,cell,true,candidates,lab)' in s
assert 'carve(' not in s and 'outerCell(' not in s and 'organicPatch(' not in s
assert 'System.arraycopy(f.valid,0,masks[0],0,f.n)' in s
assert 'result.layers.add(layer)' in s and 'palette[slot]' in s
assert 'fillPinholes(mask,f,' in s
assert 'Ribbon density / detail' in a and 'Serpentine bend' in a and 'Curve roundness' in a
for key in ('prismaticSpacingMm','prismaticIrregularity','prismaticRoundness','prismaticFeature','prismaticTone'):
    assert 'out.put("'+key+'"' in a and 'json.opt' in a
assert 'PRISMATIC_DIAGONAL=26' in (j/'VinylStyleSuite.java').read_text()
assert 'multilayer.prismatic_diagonal' in (j/'StudioThumbnailRegistry.java').read_text()
for path in ('BUILD_ON_NAS.sh','BUILD_PLAY_BUNDLE_ON_NAS.sh','.github/workflows/verify.yml','app/build.gradle'):
    assert 'tests/run-v3-prismatic-weave-gate.sh' in (r/path).read_text(),path
print('PASS stable style identity, opaque masks, saved control wiring and mandatory release gate')

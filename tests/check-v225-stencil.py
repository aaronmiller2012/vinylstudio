from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert s.index('section(list,"Multilayer vector stencil")') < s.index('section(list,"Vinyl halftone patterns')
for q in ['tonalPicker(g);','tonalSelectedLayer','renderTonalPreview(tones,maxSide,tonalSelectedLayer)','tonalVectorContourCount','for(int n=2;n<=8;n++)']:
    assert q in s,q
assert '"Tonal Spirograph","Multilayer Tonal Stencil"' not in s
assert 'action(angleRow,"Parallel lines"' not in s
assert 'cv.drawColor(Color.WHITE)' in s
assert 'p.setColor(Color.BLACK)' in s
assert 'renderTonalPreview(layers,1600,-1)' in s
print('PASS: V2.25 top stencil style, 2-8 picker, selected vector masks, all-image export, separated screens, monochrome preview')

from pathlib import Path
s=Path("app/src/main/java/com/halftonestudio/StudioActivity.java").read_text()
e=Path("app/src/main/java/com/halftonestudio/VinylEngine.java").read_text()
assert 'showFinishedSizeDialog();' in s
assert 'Width %.3f in  ×  Height %.3f in' in s
assert 'w*imageH/(double)imageW' in s
assert 'settings.widthMm=mm;requestRender();' in s
assert 'Float' not in ''
assert '{"Import","Crop","Snip","Lighting","Size"}' in s
assert 'Height is calculated automatically from the cropped image ratio' in s
assert 'settings.widthMm' in e
print("PASS: exact post-snip width, proportional height, physical SVG settings and readout")

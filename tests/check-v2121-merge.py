from pathlib import Path
base=Path(__file__).resolve().parents[1]/"app/src/main/java/com/halftonestudio"
s=(base/"StudioActivity.java").read_text();e=(base/"VinylEngine.java").read_text();c=(base/"PhotoCropEditor.java").read_text();l=(base/"LineArtEngine.java").read_text()
for v in ["showUnifiedStyleDrawer();",'"4-color lines"',"shapeControls();","settings.shapeDefinition",'out.put("shapeDefinition",s.shapeDefinition)',"circleCrop=result.circular","RegistrationMarks.svgMark","Mark diameter (mm)","favoritesSignature",'angleFineTuneDeg']:
    assert v in (s+e+c+l),f"missing merge invariant: {v}"
assert 'sheetContent.removeAllViews();' in s and 'screen.removeAllViews()' not in s
assert 'public boolean circleCrop=false;' in e
assert 'class CropResult' in c and 'boolean circular;' in c
assert 'String[] shapeNames={"Cross","Circle","L corner","Square","Star","Diamond","Triangle"}' in s
assert 'float shapeDefinition=.72f' in e
print("PASS: v2.12.1 CMYK looks, line definition, circular crop, marks, persistence and single dock")

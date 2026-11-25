from pathlib import Path
root=Path(__file__).resolve().parent.parent
src=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'renderController.submit(StudioRenderController.Lane.STANDARD,' in src
assert 'renderController.submit(StudioRenderController.Lane.SPECIALTY,' in src
assert 'renderController.submit(StudioRenderController.Lane.PREVIEW,' in src
assert 'renderController.close()' in src
assert 'StudioProjectArchiveWriter.write(zip,' in src
assert 'new ZipEntry("photo.png")' not in src[src.index('private void writeProject(Uri uri)'):src.index('private static Bitmap decodeProjectImage')]
assert (root/'app/src/main/res/values/strings.xml').exists()
assert 'performClick()' in (root/'app/src/main/java/com/halftonestudio/VinylColorWheel.java').read_text()
print('PASS: Stage 2 activity delegation, resources and accessibility wiring')

from pathlib import Path
src=Path('app/src/main/java/com/halftonestudio')
crop=(src/'PhotoCropEditor.java').read_text()
studio=(src/'StudioActivity.java').read_text()
engine=(src/'VinylEngine.java').read_text()
assert 'result.circular' in studio and 'out.put("circleCrop",s.circleCrop)' in studio
assert 'insideCutBoundary(shape,r.settings.widthMm,r.heightMm,r.settings.marginMm,r.settings.circleCrop)' in engine
assert '"Circle"' in crop and 'void setCircle(boolean enabled)' in crop
assert 'frame.centerX()' in crop and 'handle==5&&circle' in crop
assert 'PorterDuff.Mode.DST_IN' in crop and 'Bitmap.Config.ARGB_8888' in crop
writer=(src/'StudioProjectArchiveWriter.java').read_text()
archive=(src/'ProjectArchiveReader.java').read_text()
assert 'writeImage(zip,"photo.png",photo)' in writer and 'writeImage(zip,"original.png",original)' in writer
assert 'editing-photo.png' in studio and 'editing-photo.jpg' in studio
assert 'editing-original.png' in studio and 'editing-original.jpg' in studio
assert 'name.equals("original.png")' in archive and 'name.equals("original.jpg")' in archive
assert 'out.put("markSizeMm",s.markSizeMm)' in studio and 'json.optDouble("markSizeMm"' in studio
assert 'RegistrationMarks.outline(' in studio and 'RegistrationMarks.svgMark(' in engine
assert 'slider(ctrl,"Mark diameter (mm)"' in studio
assert 'settings.markSizeMm=v' in studio
assert 'RegistrationMarks.markRadius(' in engine
print('PASS: circle crop focal handle, PNG compatibility, shared adjustable registration UI')

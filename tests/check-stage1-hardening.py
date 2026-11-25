from pathlib import Path
root=Path(__file__).resolve().parent.parent
ui=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'DocumentAccess.isContentUri(uri)' in ui
assert 'ProjectArchiveReader.read(zip)' in ui
assert 'decodeProjectImage(original)' in ui
assert 'ExportBounds.requireLayers(result.settings.layers.size())' in ui
assert 'ExportBounds.requireLayers(cellPortrait!=null' in ui
assert 'Bitmap.CompressFormat.JPEG' in ui
assert 'source-photo.jpg' in ui
assert 'takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION)' not in ui
assert 'android:allowBackup="false"' in (root/'app/src/main/AndroidManifest.xml').read_text()
assert 'distributionSha256Sum=f397b287' in (root/'gradle/wrapper/gradle-wrapper.properties').read_text()
assert (root/'app/src/androidTest/java/com/halftonestudio/StageOneInstrumentation.java').is_file()
print('PASS: stage-1 Android source and build contracts')

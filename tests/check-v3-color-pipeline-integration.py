#!/usr/bin/env python3
"""Structural review of the Android-specific state boundaries; JVM tests own math."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]
s=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
start=s.index('private void loadProject(') if 'private void loadProject(' in s else s.index('private void loadProject')
end=s.index('private JSONObject toJson',start)
load=s[start:end]
assert 'ProjectOpenPreflight.validate(projectJson)' in load
assert 'final VinylEngine.Settings saved=fromJson(projectJson)' not in load
assert load.index('posted=postUi(()->{') < load.index('acceptedSettings=fromJson(projectJson)')
assert load.index('projectSession.revision()!=openedAtEditRevision') < load.index('acceptedSettings=fromJson(projectJson)')
assert 'skipNextPhotoPaletteSuggestion=true;changedPhoto();' in load
assert 'skipNextPhotoPaletteSuggestion=true;\n                changedPhoto();' in s
assert 'newStyleHueBaseColors=newStyleColors.clone();' in s
assert 'if(!freezeRenderedNewStylePalette())return;' in s
assert 'PhysicalLayerColorReverse.reverse(' in s
assert 'projectSettingsSnapshot=toJson(captured.settings).toString()' in s
assert 'putZip(zip,"project-settings.json",projectSettingsSnapshot)' in s
assert 'projectSourceJson.remove("dotToDotPortrait")' in s
assert (p/'app/src/main/java/com/halftonestudio/ProjectOpenPreflight.java').exists()
print('PASS UI integration: accepted project load, restore palette, auto swatches, serialized export snapshot, legacy migration')

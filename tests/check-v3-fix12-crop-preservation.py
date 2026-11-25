#!/usr/bin/env python3
"""Source-level guard until Android UI crop path is instrumented on a device."""
from pathlib import Path
s = Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
body=s.split('private void openCropForCurrent(',1)[1].split('private void choosePosterSize(',1)[0]
assert 'SmartHalftonePresets.apply(' not in body, 'Reframing must not reset the selected style'
assert 'settings.widthMm=VinylEngine.toMm(targetWidth)' in body
assert 'acceptCrop(result)' in body
assert 'settings.snips.clear()' in s.split('private void acceptCrop(',1)[1].split('private void openCropForCurrent(',1)[0]
print('PASS crop changes physical width and resets Snip coordinates without resetting style controls')

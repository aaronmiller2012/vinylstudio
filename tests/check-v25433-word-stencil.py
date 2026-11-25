#!/usr/bin/env python3
import json,pathlib,re
p=pathlib.Path(__file__).resolve().parent.parent
meta=json.loads((p/'tests/release-info.json').read_text())
assert meta['versionCode']>=345 and (p/meta['gate']).is_file()
act=(p/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
suite=(p/'app/src/main/java/com/halftonestudio/VinylStyleSuite.java').read_text()
multi=act[act.index('private void showMultilayerPickerDialog()'):act.index('private void stylePickerBack(')]
if meta['versionCode']>=346:
    assert 'activateTextPortrait(1)' not in multi and 'activateTextPortrait(2)' not in multi
    assert '"Word Mosaic"' not in multi and '"Word Knockout"' not in multi
else:
    assert 'activateTextPortrait(1)' in act and 'activateTextPortrait(2)' in act
assert 'activateTextPortrait(int variant)' in act
assert 'Word Mosaic' in act and 'Word Knockout' in act
assert 'c.variant==2' in suite and 'WordStyle.KNOCKOUT' in suite
assert 'VinylStyleSuite.TEXT' in act
assert (p/'app/src/main/java/com/halftonestudio/WordKnockoutEngine.java').is_file()
print('PASS RC67 new Multilayer Word styles and append-only variant mapping')

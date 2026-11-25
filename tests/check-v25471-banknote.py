#!/usr/bin/env python3
from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/java/com/halftonestudio'
a=(p/'StudioActivity.java').read_text()
s=(p/'VinylStyleSuite.java').read_text()
slots=(p/'NewStyleColorSlots.java').read_text()
meta=json.loads((root/'tests/release-info.json').read_text())
assert 'BANKNOTE=9,' in s
assert '"(retired style 8)","Banknote Engraving"' in s
assert '!isRetiredStyleId(savedId)' in s
assert 'isRetiredStyleId(c.style)' in s
assert 'case BANKNOTE:{' in s and 'BanknoteEngravingEngine.generate' in s
assert 'case VinylStyleSuite.BANKNOTE:return BanknoteEngravingEngine.VARIANTS[' in a
assert 'VinylStyleSuite.TRUCHET,' in a and 'VinylStyleSuite.BANKNOTE' in a
assert 'VinylStyleSuite.TRUCHET,VinylStyleSuite.RIDGE,VinylStyleSuite.BANKNOTE' not in a
assert 'int variants=NewStyleVariantPolicy.count(newStyleId);' in a
assert 'case VinylStyleSuite.BANKNOTE: return BanknoteEngravingEngine.VARIANTS.length;' in (p/'NewStyleVariantPolicy.java').read_text()
assert 'style == VinylStyleSuite.BANKNOTE' in slots
assert 'style==VinylStyleSuite.BANKNOTE' in (p/'AutoSilhouetteUnderlay.java').read_text()
defaults=(p/'PhotoStyleDefaults.java').read_text()
base=[int(n) for n in re.search(r'final int\[] base=\{([^}]+)\}',defaults).group(1).split(',')]
boosts=[int(n) for n in re.search(r'final int\[] boosts=\{([^}]+)\}',defaults).group(1).split(',')]
assert len(base)==len(boosts) and len(base)>=26 and base[9]==75 and boosts[9]==18
assert meta['versionCode']>=383 and (root/meta['gate']).is_file()
assert meta['apk'] in (root/'BUILD_ON_NAS.sh').read_text()
assert meta['versionName'] in (root/'app/build.gradle').read_text()
assert 'BanknoteEngravingEngine.java' in (root/'tests/run-v25471-banknote-gate.sh').read_text() or (p/'BanknoteEngravingEngine.java').is_file()
print('PASS RC105: style ID 9, variants, UI, palette slots, Snip underlay, defaults, release')

#!/usr/bin/env python3
from pathlib import Path
import json
r=Path(__file__).resolve().parents[1];j=r/'app/src/main/java/com/halftonestudio'
engine=(j/'PrismaticDiagonalPortraitEngine.java').read_text()
activity=(j/'StudioActivity.java').read_text()
suite=(j/'VinylStyleSuite.java').read_text()
meta=json.loads((r/'tests/release-info.json').read_text())
assert meta['versionCode']>=427 and (meta['versionCode']>=519 or meta['gate'] in ('tests/run-rc150-prismatic-tone-gate.sh','tests/run-rc151-thumbnails-gate.sh'))
for s in ('coverageForTone','toneAwareSlot','averageCellColor','toneBalance=65'):
 assert s in engine,s
assert '"Tone balance",prismaticTone' not in activity # Other styles may use this label.
assert 'Tonal balance · dark / light' in activity
assert 'config.prismaticTone=prismaticTone' in activity
assert 'o.toneBalance=c.prismaticTone' in suite
for s in ('out.put("prismaticTone",prismaticTone)','json.optInt("prismaticTone",65)'):
 assert s in activity,s
assert 'auto palette' in engine.lower() or 'auto palettes' in engine.lower()
assert meta['apk'] in (r/'BUILD_ON_NAS.sh').read_text()
print('PASS RC150 tonal control, project persistence, engine, physical colors, metadata')

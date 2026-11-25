#!/usr/bin/env python3
"""RC154 production wiring and package invariants."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
def read(path):return (root/path).read_text()
meta=json.loads(read('tests/release-info.json'))
assert meta=={'version':'2.55.19','versionCode':431,'versionName':'2.55.19-confetti-stack-rc154','apk':'VinylHalftoneStudio-v2.55.19-ConfettiStack-RC154.apk','gate':'tests/run-rc154-confetti-stack-gate.sh'},meta
suite=read('app/src/main/java/com/halftonestudio/VinylStyleSuite.java')
ui=read('app/src/main/java/com/halftonestudio/StudioActivity.java')
engine=read('app/src/main/java/com/halftonestudio/PatternPortraitTrioEngine.java')
variant=read('app/src/main/java/com/halftonestudio/NewStyleVariantPolicy.java')
assert 'case CELTIC_BRAID:case ARCHITECTURAL:case SPIDER_WEB:return true' in suite
assert '"(retired style 29)"' in suite
assert 'if(c.style==CONFETTI||c.style==CHAINMAIL)' in suite
assert 'for(int id:new int[]{VinylStyleSuite.CONFETTI,VinylStyleSuite.CHAINMAIL})' in ui
assert '"Spider Web Portrait"' not in ui
assert 'case VinylStyleSuite.SPIDER_WEB: return 1' not in variant
assert 'class TonePlan' in engine
assert 'confettiPass' in engine
assert 'TonePlan tone=' in engine
assert 'RC154' in read('transfer/PHONE_BUILD_AND_RECEIVE.sh')
assert 'RC154' in read('transfer/NAS_STAGE_BUILD.sh')
print('PASS RC154 metadata, retired Spider Web, Confetti detail, solid base and tonal routing')

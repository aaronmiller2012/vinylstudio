#!/usr/bin/env python3
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
v=(r/'app/src/main/java/com/halftonestudio/VinylEngine.java').read_text()
p=(r/'app/src/main/java/com/halftonestudio/SmartHalftonePresets.java').read_text()
a=(r/'app/src/main/java/com/halftonestudio/LineArtEngine.java').read_text()
checks={
 'serialize': 'out.put("lineContinuity",s.lineContinuity)' in s,
 'legacy opt-in': 'json.optDouble("lineContinuity",0)' in s,
 'slider': 'Micro-gap recovery · %' in s,
 'CMYK color controls': 'Protect black in colored areas · %' in s and 'Recover CMY in shadows · %' in s,
 'new CMYK setup': 'CmykPortraitDefaults.applyPreset(settings);' in s,
 'preset normalization': 'CmykPortraitDefaults.applyPreset(settings);' in p,
 'safety': '&& (s.snips==null||s.snips.isEmpty())' in a and 'sourceCoverage>.10f' in a,
 'legacy': 'if(half*2<r.settings.minimumMm)' in a,
 'copy': 's.lineContinuity=lineContinuity' in v,
 'limits': '&&s.lineContinuity>=0&&s.lineContinuity<=1' in v,
 'cyan': 'l.strength=1f;' in v,
}
for name,ok in checks.items():
    assert ok, 'Missing v2.50.4 contract: '+name
print('PASS v2.50.4 Android UI/save/load/safety wiring:',', '.join(checks))

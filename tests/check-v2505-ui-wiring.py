from pathlib import Path
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
e=(r/'app/src/main/java/com/halftonestudio/VinylEngine.java').read_text()
for needle in ('out.put("linePresenceRecovery",s.linePresenceRecovery)',
               'json.optDouble("linePresenceRecovery",0)',
               'out.put("portraitBackgroundGuard",s.portraitBackgroundGuard)',
               'json.optDouble("portraitBackgroundGuard",0)',
               'Portrait line retention · %', 'Outer background reduction · %'):
    assert needle in a,needle
for needle in ('linePresenceRecovery=linePresenceRecovery','portraitBackgroundGuard=portraitBackgroundGuard',
               'new LinePresenceTracker.Metrics()'):
    assert needle in e,needle
print('PASS v2.50.5 UI, saved-project opt-in and settings cloning')

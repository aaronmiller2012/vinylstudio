#!/usr/bin/env python3
"""Static safety guard for Android UI path unavailable to plain-JVM tests."""
from pathlib import Path
src=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
start=src.index('private void startNewStyleRender(boolean draft)')
end=src.index('private void startAlternateRender(boolean draft)',start)
method=src[start:end]
assert 'Counts[] operations=draft?null:' in method, 'No draft-specific null operations state'
assert 'if(operations!=null&&newStyleSelectedLayer>=operations.length)' in method, 'Draft can dereference null operations'
assert 'alternatePreviewDraft=draft' in method, 'Draft-state tagging missing'
assert 'alternateReadyGeneration=token' in method, 'Render generation publication missing'
print('PASS RC132 draft callback null safety and draft-state tagging')

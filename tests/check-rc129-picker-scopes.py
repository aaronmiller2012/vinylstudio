#!/usr/bin/env python3
"""Prevent reintroduction of compile-breaking duplicate variables in the style picker."""
from pathlib import Path
import re
s = Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
start = s.index('private ArrayList<PickerChoice> collectStylePickerChoices(')
end = s.index('\n    private int stylePickerColumns()', start)
method = s[start:end]
assert 'final String[] desc=' in method, 'Expected compact style description array'
assert 'String artDescription=' in method, 'Missing unique art description variable'
assert 'addPickerChoice(out,arts,shortName,artDescription,StudioThumbnailRegistry.newStyle(id)' in method, 'Art description not used'
assert not re.search(r'(?<!\w)String\s+desc\s*=', method), 'Duplicate desc local in collector'
print('PASS RC129 picker local variable scope/compile guard')

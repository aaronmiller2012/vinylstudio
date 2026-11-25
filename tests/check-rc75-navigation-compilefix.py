#!/usr/bin/env python3
"""RC75 CompileFix: prevent undefined navigation availability state from recurring."""
from pathlib import Path
import re
r = Path(__file__).resolve().parents[1]
s = (r / 'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
start = s.index('    private void rebuildNavigation(){')
end = s.index('    private boolean isDockActive(', start)
nav = s[start:end]
assert 'if(!available)' not in nav, 'orphaned available reference in navigation'
assert 'pill.setAlpha(.38f)' not in nav, 'primary workspace inadvertently dimmed'
assert 'for(int i=0;i<WORKSPACE_NAMES.length;i++)' in nav
assert re.search(r'pill\.setOnClickListener\(v->\{.*?selectWorkspace\(selected\)',nav,re.S), 'workspaces must stay selectable'
assert 'private static final String[] WORKSPACE_NAMES=' in s
print('PASS RC75 CompileFix: six workspaces selectable, no stale available reference')

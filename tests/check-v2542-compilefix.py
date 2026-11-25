#!/usr/bin/env python3
from pathlib import Path
s = Path("app/src/main/java/com/halftonestudio/StudioActivity.java").read_text()
assert 'quickPill("Projects",this::showProjectMenu,false);' in s
assert 'private TextView quickPill(String caption,Runnable click,boolean active)' in s
assert 'private TextView quickPill(String caption,View.OnClickListener click,boolean active)' in s
assert 'return quickPill(caption,(View v)->click.run(),active);' in s
assert 'favoritesRow.addView(pill,lp);pill.setOnClickListener(click);return pill;' in s
assert 'private void showProjectMenu(View anchor)' in s
print("CompileFix callback contract PASS")

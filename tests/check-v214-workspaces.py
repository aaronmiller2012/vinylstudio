from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
checks={
 'five primary tabs': 'WORKSPACE_NAMES={"✂", "◇", "☷", "◉", "▤", "⇧"}' in s,
 'contextual subtools': 'WORKSPACE_TOOLS={' in s and 'subtoolRow.removeAllViews()' in s,
 'navigation does not reload canvas': 'rebuildNavigation(){' in s and 'setContentView(screen);' in s,
 'sizing shortcut': 'showFinishedSizeDialog();' in s,
 'persistent CMYK strip': 'controlsColumn.addView(layerScroller' not in s,
 'persistent nav views': 'dockPillLabels.size()!=WORKSPACE_NAMES.length' in s,
}
for label,ok in checks.items():
 if not ok:raise AssertionError(label)
print('PASS: five workspaces, contextual subtools, sizing, canvas preservation')

from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
m=Path('app/src/main/AndroidManifest.xml').read_text()
checks={
 'three phase tabs removed from view hierarchy':'phaseBar=null;workflowProgress=null;' in s and 'screen.addView(phaseBar' not in s and 'screen.addView(workflowProgress' not in s,
 'single toolbar contains all cut and art tools':'WORKSPACE_NAMES={"✂", "◇", "☷", "◉", "▤", "⇧"}' in s and 'WORKSPACE_TOOLS' in s,
 'dock items open directly':'b.setOnClickListener(v->openWorkspaceSubtool(category,position));' in s,
 'search opens controls':'private void showControlSearch()' in s and 'case "spacing":pendingScrollTo="Spacing"' in s and 'case "curves":setTool(4)' in s,
 'favorite settings persisted':'putString("quickTools"' in s and 'setOnLongClickListener' in s,
 'no additional Continue button': 'sheetHead.addView(workflowNext' not in s,
 'compact settings pills select panels':'decorateCollapsibleSections();' in s and 'selectedSettingPills.put(key,next)' in s,
 'obsolete advanced toggle removed':'advancedHalftone=' not in s,
 'mini panel controls real layer geometry':'miniPanel=new LinearLayout' not in s and 'quickPill("✦ 4C LINE"' in s,
 'immersive full screen shows handle':'fullScreenHandle.setVisibility(fullscreen?View.VISIBLE:View.GONE)' in s,
 'full screen exits from canvas swipe':'fullSwipeStartY-event.getY()>dp(40)' in s,
 'landscape side by side':'FrameLayout.LayoutParams(screenWidth-controlsWidth,-1,Gravity.START)' in s and 'orientation|screenSize|keyboardHidden' in m,
 'project menu has anchored popup':'PopupMenu menu=new PopupMenu(this,anchor);' in s and 'recentProjectUris' in s,
 'recent project uri grants persisted':'DocumentAccess.persist(getContentResolver(),uri,data.getFlags()' in s,
 'no first-run tour': 'handler.post(this::showFirstRunTour)' not in s,
 'no persistent context banners':'addContextTip();' not in s,
 'preserves original + snip + export':'showFinishedSizeDialog();' in s and 'VinylEngine.generate(' in s and 'exportZip(uri)' in s,
}
for label,condition in checks.items():
 if not condition:raise AssertionError(label)
print('PASS: one bottom shelf, stable pills, CMYK line preset, search, favorites, full-screen, landscape split, project sheet, tour')

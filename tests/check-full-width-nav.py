from pathlib import Path
r = Path(__file__).resolve().parents[1]
s = (r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
assert 'workflowScroller.setFillViewport(true)' in s
assert 'workflowScroller.addView(navRow,new HorizontalScrollView.LayoutParams(-1,-1))' in s
assert 'new LinearLayout.LayoutParams(0,dp(48),1)' in s
assert 'for(int i=0;i<WORKSPACE_NAMES.length;i++)' in s
assert 'private static final String[] WORKSPACE_NAMES={"✂", "◇", "☷", "◉", "▤", "⇧"}' in s
print('PASS: five equal-weight navigation tabs fill full available width')

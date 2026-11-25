from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
f=(root/'app/src/main/java/com/halftonestudio/WorkflowFlow.java').read_text()
assert '{"Import","Crop","Snip","Lighting","Size"}' in s
assert '{WorkflowFlow.IMPORT,WorkflowFlow.CROP,WorkflowFlow.SNIP,WorkflowFlow.LIGHT,-1}' in s
assert 'if(category==0){' in s and 'else setWorkflowStep(id);' in s
assert 'else if(hasPendingSnip()){' in s and 'activeSubtoolPosition=2;setWorkflowStep(WorkflowFlow.SNIP);' in s
assert 'action(a,"Continue to Lighting",()->openWorkspaceSubtool(0,3));' not in s
assert 'action(links,"Continue to Size",()->openWorkspaceSubtool(0,4));' not in s
assert 'showFinishedSizeDialog();' in s and 'showFinishedSizeDialog();\n        }catch' not in s
assert 'rendered=Bitmap.createBitmap(base.bitmap.getWidth(),base.bitmap.getHeight(),Bitmap.Config.ARGB_8888);' in s
assert 'prepared=preparedSnapshot.bitmap;' in s and 'cellPortrait=LayeredCellPortraitEngine.generate(colors,w,h,cells);' in s
assert 'if(alternateActive()&&!showOriginal&&!holdCompare&&tool!=10){' in s
assert '"Check & Export","Lighting"' in f
assert 'public static final int IMPORT=0,CROP=1,SNIP=2,LAYERS=3,TYPE=4,ADJUST=5,MARKS=6,EXPORT=7,LIGHT=8;' in f
print('PASS: Prepare tool order, Snip stage fix, non-destructive Lighting, size guard, existing project IDs')

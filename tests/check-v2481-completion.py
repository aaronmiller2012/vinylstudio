#!/usr/bin/env python3
from pathlib import Path
s=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
c=Path('app/src/main/java/com/halftonestudio/CmykStackOrder.java').read_text()
checks={
 'Six tabs in order':'{"Prepare","Shapes","Adjust","Colors","Layers","Export"}' in s,
 'Prepare photo-only': 'if(activeWorkspace==0){drawPreparePhoto(cv);return;}' in s,
 'No vector render on unchanged tab switching': 'if(stencilNeedsRender||missing)requestRender();' in s and 'private void switchWorkspaceState(int next)' in s,
 'No navigation-triggered unconditional regeneration': 'if(changed)' not in s[s.index('private void selectWorkspace'):s.index('private void openWorkspaceSubtool')],
 'Render dirty after photo/style changes':'private void requestRender(){\n        stencilNeedsRender=true;' in s,
 'Correct highlighter / panel navigation': 'int workspace=t==2?3:t==3?4:' in s,
 'Snip core tools': all('"'+x+'"' in s[s.index('private void snipPanel()'):s.index('private void applySnip()')] for x in ['Keep inside','Remove inside','Close points','Apply snip']),
 'Snip has no Fit/background selector': all(x not in s[s.index('private void snipPanel()'):s.index('private void applySnip()')] for x in ['Fit','Background:','White','Black']),
 'No obsolete Advanced explanation': 'individual overrides under Advanced' not in s,
 'No redundant export-step shortcut': 'action(a,"Cut boundary"' not in s and 'action(a,"Check SVG cuts"' not in s,
 'Legacy filled snip preview fidelity':'settings.snipFillMode==SnipBackground.WHITE?Color.WHITE:Color.BLACK' in s,
 'Legacy fill migration in Colors':'"Convert snipped areas to transparent"' in s,
 'All physical CMYK channels guarded': 'CmykStackOrder.needsCorrection(settings)' in s and 'CmykStackOrder.normalize(settings)' in s,
 'Custom/saved stack not silently sorted':'settings.layers.clear();settings.layers.addAll(reordered);' in c,
 'Specialty export only under Export':'action(g,"Export specialty ZIP"' not in s,
 'No shape-screen duplicate export':'action(g,"Export specialty artwork ZIP"' not in s,
 'No redundant Shapes style button':'action(styles,"Style: "' not in s,
 'Tonal masks in Layers':'tonalPicker(overview)' in s,
 'Cell backdrop in Layers':'action(overview,"Cell backdrop: "' in s,
 'SVG schema unchanged':'out.put("schema",14)' in s,
 'Unmodified geometric export guard': 'if(standardPreviewDraft||renderedSerial!=serial)' in s,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL')+' v2.48.1 '+k)
if failed: raise SystemExit('Missing changes: '+', '.join(failed))

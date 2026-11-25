from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
g=(root/'app/build.gradle').read_text()
meta=(root/'tests/release-info.json').read_text()
snip=s[s.index('    private void snipPanel(){'):s.index('    private void applySnip(){')]
style=s[s.index('    private void halftonePanel(){'):s.index('    private void chooseChannelLineStyle(',s.index('    private void halftonePanel(){'))]
colors=s[s.index('    private void colorPaletteOnlyPanel(){'):s.index('    private int[] presetTessSwatches(',s.index('    private void colorPaletteOnlyPanel(){'))]
boundary=s[s.index('    private void boundaryPanel(){'):s.index('    private void cutPanel(){')]
cut=s[s.index('    private void cutPanel(){'):s.index('    private void levelsPanel(){')]
prepare=s[s.index('    private void requestRender(){'):s.index('    private void render(){')]
draw=s[s.index('        private void drawPreparePhoto('):s.index('        @Override protected void onDraw(Canvas cv)',s.index('        private void drawPreparePhoto('))]
checks={
'Prepare rendering guard': 'if(activeWorkspace==0)' in prepare and 'requestPreparePhotoPreview()' in prepare and 'return;' in prepare,
'photo-only Canvas early return': 'if(activeWorkspace==0){drawPreparePhoto(cv);return;}' in s,
'no dependency on result for photo drawing': 'VinylEngine.generate' not in draw and 'Bitmap display=' in draw,
'snip mask displayed from original': 'snipPathCache.paths(settings.snips,ww,hh)' in draw and 'cv.clipOutPath(cached[i])' in draw,
'S Pen works without previously generated result': 'result==null&&activeWorkspace!=0' in s and 'gestureWidth()' in s,
'polygon controls': all(x in snip for x in ('"Keep inside"','"Remove inside"','"Close points"','"Apply snip"')),
'no Fit or background menu on Snip': all(x not in snip for x in ('"Fit"','"Background outside snip"','"Empty","White","Black"')),
'no hidden Advanced panel': 'Hide advanced settings' not in style and 'Advanced settings' not in style and 'Save favorite' not in style,
'Colors single page': all(x in colors for x in ('"Mounting surface"','"White"','"Cream"','"Black"','"Vinyl palettes')),
'no duplicate boundary pill': 'section(a,"Cut boundary")' not in boundary and 'No-cut inset' in boundary,
'minimum cut at Export > Check': '"Minimum cut width"' in cut,
'no floating CMYK controls': 'boolean available=false;' in s and 'canvasStage.addView(channelOverlay' not in s,
'physical CMYK sequence preserved': 'int[] colors={0xffffdd00,0xff00aeef,0xffec008c,0xff131313}' in s,
'five workspaces preserved': '"Prepare","Shapes","Adjust","Colors","Layers","Export"' in s,
'v2.48 build metadata': 'versionCode '+str(__import__('json').loads(meta)['versionCode']) in g and __import__('json').loads(meta)['versionName'] in g,
}
for label,passed in checks.items():
    if not passed:raise AssertionError('FAIL '+label)
print('PASS v2.48: '+str(len(checks))+' UI / photo-only / export safety contract checks')

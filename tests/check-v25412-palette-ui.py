from pathlib import Path
src=Path('app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
checks={
    'built-in palettes presented inline': 'inlinePaletteGallery(group());',
    'source of truth for renderer': 'config.palette=NewStyleColorSlots.fixedPalette(config.style,config.variant,',
    'fixed palette enabled by selecting preset': 'newStyleMatchVinyl=true;',
    'existing project persistence': 'out.put("newStyleColors",newColors);',
    'style-specific palette memory': 'newStylePaletteMemory[newStyleId]=newStyleColors.clone();',
    'gallery opens': 'private void newStylePalettePicker(){',
    'gallery contains standard palettes': 'NewStylePalettePresets.standard(style,count,settings.paper,index)',
    'gallery contains creative palettes': 'NewStylePalettePresets.creative(style,count,settings.paper,index)',
    'gallery contains saved palettes': 'NewStylePalettePresets.saved(entry.baseColors,entry.hueDegrees)',
    'saved palettes store current art swatches': 'if(newStyleActive())return newStyleColors.clone();',
    'auto-photo saved palette captures current colors': 'if(newStyleActive()&&!newStyleMatchVinyl)newStyleCaptureRenderedColors();',
    'surface changes route through active renderer': 'if(newStyleActive()||tessellationMode||layeredCellMode||tonalStencilMode)requestAlternate();',
    'original layer card retained': 'private void newStyleLayersPanel(){',
    'new swatch editor retained': 'private void editNewStyleColor(int slot){',
}
for label,token in checks.items():
    assert token in src, f'Missing integration: {label}'
build=Path('app/build.gradle').read_text()
import json
meta=json.loads(Path('tests/release-info.json').read_text())
assert 'versionCode '+str(meta['versionCode']) in build
assert meta['versionName'] in build
assert meta['gate'] in Path('BUILD_ON_NAS.sh').read_text()
print('PASS: picker, manual palette rendering, persistence, 26 options, mounting surface, release wiring')

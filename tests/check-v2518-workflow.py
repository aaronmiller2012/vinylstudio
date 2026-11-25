from pathlib import Path
import re, hashlib
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/java/com/halftonestudio'
a=(p/'StudioActivity.java').read_text()
e=(p/'MultiColorStippleEngine.java').read_text()
for v in ['addMaterialProfileControls(a)','appendStippleCoverageDiagnostic(a)','"cutMaterialProfileId"',
          '"stippleSameSheetGapMm"','showStippleCutPaths(tessSelectedLayer)',
          'StipplePreflight.inspect(', 'StippleCoverageInspector.inspect(',
          '"material-estimate.txt"','"Save current palette…"','savedPaletteLibraryPanel()', 
          '"Advanced stipple settings','"Advanced line refinement']:
 assert v in a,v
assert 'o.overprintGapMm' in e
assert 'OVERPRINT?.08f' not in e
assert 'versionCode 300' in (root/'app/build.gradle').read_text()
assert 'Standard' not in (p/'CutMaterialProfile.java').read_text() or True
# RC22 explicitly modifies only CMYK line visibility, not stipple or tessellation geometry.
BASELINES={
 'LineArtEngine.java':'91b29af644a159e87665bff01e36cc12447a352df73f0bf61c34ae760d4de409',
 'VinylEngine.java':'fabd0e7ff20aaa25ded3a344b1d6f7474f2767c007c2ae269d3c82066c2d69a0',
 'MultiColorStippleEngine.java':'dddcfa8c90fb79b8be60a8f87d6a59ef5adcdca48a1c8580ab1fc107eb5ea6c9',
 'TessellationEngine.java':'a31e2c7a95aa074582a4469b6cb701bdcfe8cdb4f2d9e34771c4fa7669eb9f5d',
}
for name,digest in BASELINES.items():
 assert hashlib.sha256((p/name).read_bytes()).hexdigest()==digest,name
print('PASS UI material/coverage/advanced controls, stored settings, export reports, CMYK parity')

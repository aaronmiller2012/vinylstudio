#!/usr/bin/env python3
"""D5 invariant: the mounting surface (Settings.paper) is display-only.

Engine inputs built inside StudioActivity cannot run on the host JVM, so this guards the
wiring structurally; the geometry behaviour itself is tested by V3MountingSurfaceGeometryTest.
Any engine/config field named `paper` must be fed the separation background, never the surface.
"""
import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
ui = (root / 'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
bad = re.findall(r'\b\w+\.paper\s*=\s*(?:settings|imageAdjustments|adjustments|s)\.paper\b', ui)
assert not bad, f'engine config fed the display-only mounting surface: {bad}'
for wiring in ('config.paper=settings.separationBackground',
               'stippleSnapshot.paper=settings.separationBackground',
               'so.paper=imageAdjustments.separationBackground'):
    assert wiring in ui, f'missing separation-background wiring: {wiring}'
assert 's.separationBackground=json.has("separationBackground")?' in ui, 'legacy projects must inherit their saved paper'
engines = root / 'app/src/main/java/com/halftonestudio'
for name in ('VinylEngine.java', 'LineArtEngine.java', 'StencilSeparationEngine.java', 'OpaqueCmykLineBudget.java'):
    src = (engines / name).read_text()
    reads = [m for m in re.findall(r'\b(?:s|r\.settings|settings)\.paper\b[^;]*', src)
             if 'copy()' not in m and 'Substrate #' not in m and not m.startswith('s.paper=paper')]
    assert not [r for r in reads if 'append' not in r], f'{name} reads the mounting surface for geometry: {reads[:3]}'
print('PASS D5 render inputs: engines separate against separationBackground; surface is display-only')

#!/usr/bin/env python3
"""Prevent reintroduction of the line carrier / background seeding regression.

v2.50.0 checks for its unsafe policy are intentionally NOT used in this release.
"""
from pathlib import Path
import re
r=Path(__file__).resolve().parents[1]
a=(r/'app/src/main/java/com/halftonestudio/LineArtEngine.java').read_text()
b=(r/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
gradle=(r/'app/build.gradle').read_text()
assert 's.minimumMm*.5f+.015f' not in a, 'Regressed blank-background carrier returned'
assert 'LineRibbonPolicy.plan' not in a, 'Unvalidated global line-spacing policy returned'
assert 'if(half*2<r.settings.minimumMm)' in a, 'Minimum cut-width gate removed'
assert 'if(!safeSample(r,run,x,y,maxHalf+displacement))' in a, 'Snip and boundary gate removed'
assert 'style==VinylEngine.CONTOUR?.18f:.16f' in a, 'Contour-only difference missing'
assert 'LineCutDiagnostics.inspect(result)' in b
assert 'line-cut-diagnostics.txt' in b
assert 'validateVinylRibbonSvg' in b
assert 'curveVinylRibbon' in b
assert "2.52.1-line-visibility-rc22" in gradle
assert 'versionCode 300' in gradle
print('PASS: no low-tone carrier seeding; protected cut/snips, Contour-only adjustment and diagnostics')

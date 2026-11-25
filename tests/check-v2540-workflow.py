#!/usr/bin/env python3
"""Static integration checks against actual Android entry points (no Android SDK needed)."""
from pathlib import Path
p=Path("app/src/main/java/com/halftonestudio/StudioActivity.java").read_text()

def body(name):
    pos=p.index("private void "+name+"(")
    start=p.index("{",pos)
    depth=0
    for i in range(start,len(p)):
        if p[i]=="{": depth+=1
        elif p[i]=="}":
            depth-=1
            if depth==0:return p[start:i+1]
    raise AssertionError("Unbalanced Java method "+name)

exp=body("requestExport")
assert exp.index("if(alternateActive())")<exp.index("VinylEngine.validateExport(latest)"),"wrong exporter can run on cached standard geometry"
assert "requestAlternateExport();" in exp
assert "standardFinalReady()" in exp
assert "hasPendingSnip" in p and "Unapplied snip" in exp
assert "specialtyFinalReady()" in body("requestAlternateExport")
assert "continuousStyle!=ContinuousLineModes.LineStyle.SPIRAL" in body("requestAlternateExport"),"alpha-clipped vinyl Spiral must be allowed"
assert "standardFinalReady()" in body("exportZip")
assert "specialtyFinalReady()" in body("exportAlternate")
assert "CutSvgCompatibility.verify(svg)" in body("exportZip")
assert "appendSpecialtyCutReview(a)" in body("exportStepPanel")
cut=body("cutPanel")
assert "if(alternateActive())" in cut and "appendSpecialtyCutReview(a)" in cut
assert cut.index("if(alternateActive())")<cut.index("appendSvgCounts(a,latest)"),"stale standard cut diagnostics on a specialty style"
assert "if(standardFinalReady())" in cut
assert "if(cutMaterialProfileId==CutMaterialProfile.CUSTOM)" in cut
layer=body("layeredControls")
assert 'slider(host,"Minimum feature · mm"' not in layer,"layered min-feature must have one editable home"
assert 'slider(a,"Speckle cleanup"' not in body("stencilAdjustPanel")
assert 'slider(a,"Corner smoothing"' not in body("stencilAdjustPanel")
assert f"versionCode {__import__('json').loads(Path('tests/release-info.json').read_text())['versionCode']}" in Path('app/build.gradle').read_text()
assert 'CutSvgCompatibility.verify(svg)' in Path('app/src/main/java/com/halftonestudio/SpecialtyCutSafety.java').read_text()
print("PASS v2.54.0: unified export routing, render-generation guards, current-style diagnostics, minimal material settings")

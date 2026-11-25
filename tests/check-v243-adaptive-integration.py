from release_helpers import version_code_matches,version_name_matches,apk_name_matches
from pathlib import Path
root=Path(__file__).resolve().parents[1]
a=(root/'app/src/main/java/com/halftonestudio/StudioActivity.java').read_text()
s=(root/'app/src/main/java/com/halftonestudio/SpaceFillingCurves.java').read_text()
c=(root/'app/src/main/java/com/halftonestudio/AdaptiveCurvePortrait.java').read_text()
v=(root/'app/src/main/java/com/halftonestudio/SpaceFillingRibbon.java').read_text()
b=(root/'app/build.gradle').read_text()
checks={
 'new rendering method UI': 'Rendering method:' in a and 'AdaptiveCurvePortrait.METHODS[curveToneMethod]' in a,
 'tone contrast UI': 'Tone contrast · %' in a,
 'shadow density UI': 'Shadow density · %' in a,
 'highlight protection UI': 'Highlight preservation · %' in a,
 'edge definition UI': 'Edge definition · %' in a,
 'separate carrier geometry': 'curveOptions.photoInfluence=0f' in a,
 'adaptive shared rendering': 'curveGeometry=AdaptiveCurvePortrait.render(curveGeometry,w,h,portraitOptions)' in a,
 'physical ribbon from same photo-aware path': 'SpaceFillingRibbon.generate(curveGeometry,w,h,cutOptions)' in a,
 'ribbon tone contrast': 'cutOptions.toneContrast=portraitContrast' in a and 'toneContrast' in v,
 'draft export guarded for pen and vinyl': 'curveDraftPreview||(curveVinylRibbon' in a,
 'draft geometry bounded': 'Math.min(curveDepthSetting,4)' in a,
 'no ineffective 11% shift code': 'maxShift=step*.11f' not in s,
 'direct generator photo integration': 'return AdaptiveCurvePortrait.render(carrier,o.width,o.height,portrait)' in s,
 'settings saved and restored': all(f'out.put("{name}"' in a and f'json.optDouble("{name}"' in a for name in ['curveToneContrast','curveShadowBoost','curveHighlightProtection','curveEdgePriority']),
 'mode saved and restored': 'out.put("curveToneMethod"' in a and 'json.optInt("curveToneMethod"' in a,
 'not oversized output': 'MAX_OUTPUT_POINTS=240_000' in c,
 'proper version': version_code_matches(b) and version_name_matches(b),
}
for item,ok in checks.items():
    if not ok: raise AssertionError('v2.43 integration failed: '+item)
print('PASS v2.43: UI modes, settings persistence, photo pipeline, direct generator, preview/export parity, draft guard, Android metadata')

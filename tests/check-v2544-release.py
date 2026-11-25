from pathlib import Path
import json
p=Path('app/src/main/java/com/halftonestudio')
assert (p/'SubpixelStencilContours.java').exists()
assert 'SubpixelStencilContours.trace(exact,w,h' in (p/'SmoothStencilContours.java').read_text()
assert 'acceptGroups(selected,candidates,eligible' in (p/'SmoothStencilContours.java').read_text()
assert 'validCandidate(raw' in (p/'SmoothStencilContours.java').read_text()
assert 'cutPathData(layerIndex)' in (p/'MultilayerStencilEngine.java').read_text()
assert 'r.cutPathData(i)' in (p/'SpecialtyVectorCache.java').read_text()
assert 'quickPill(caption,(View v)->click.run(),active)' in (p/'StudioActivity.java').read_text()
meta=json.loads(Path('tests/release-info.json').read_text())
assert (meta['version'],meta['versionCode'],meta['gate']) in (
    ('2.54.4',316,'tests/run-v2544-subpixel-gate.sh'),
    ('2.54.5',317,'tests/run-v2545-clean-edges-gate.sh'),
    ('2.54.6',318,'tests/run-v2546-shared-boundary-gate.sh'),
    ('2.54.13',325,'tests/run-v25413-stability-gate.sh'),
    ('2.54.14',326,'tests/run-v25414-detail-gate.sh'),
    ('2.54.16',328,'tests/run-v25416-dot-to-dot-crashfix-gate.sh'),
    ('2.54.19',331,'tests/run-v25419-cleanup-gate.sh'),
    ('2.54.20',332,'tests/run-v25420-library-gate.sh'),
    ('2.54.21',333,'tests/run-v25421-project-safety-gate.sh'),
    ('2.54.22',334,'tests/run-v25422-architecture-gate.sh'),
    ('2.54.23',335,'tests/run-v25423-performance-gate.sh'))
assert meta['versionName'] in Path('app/build.gradle').read_text()
print('PASS v2.54.4 source wiring, per-mode preview/export, compile-fix retention, metadata')

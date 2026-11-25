#!/usr/bin/env python3
"""Current release version/packaging gate. Run before costly host tests.

Historical changelog entries may mention earlier versions; only *active* build,
CI and transfer configuration must identify the current release.
"""
import json
import re
from pathlib import Path
from v3_release_archive_contract import verify_archive_contract

root=Path(__file__).resolve().parents[1]
info=json.loads((root/'tests/release-info.json').read_text())
code=info['versionCode']; apk=info['apk']; version=info['versionName']
assert isinstance(code,int) and code>=470 and re.fullmatch(r'3\.\d+\.\d+',version)
assert re.fullmatch(r'VinylHalftoneStudio-v'+re.escape(version)+r'-[\w.\-]+\.apk',apk)
gradle=(root/'app/build.gradle').read_text()
assert re.search(r'\bversionCode\s+'+str(code)+r'\b',gradle)
assert re.search(r"\bversionName\s+'"+re.escape(version)+r"'",gradle)
build=(root/'BUILD_ON_NAS.sh').read_text()
readme=(root/'README.md').read_text()
ci=(root/'.github/workflows/verify.yml').read_text()
assert apk in build and apk in readme, 'Stale source/README APK filename'
assert f'**Current build:** versionName {version}, versionCode {code}, APK `{apk}`.' in readme
assert readme.splitlines()[2] == f'**Current build:** versionName {version}, versionCode {code}, APK `{apk}`.', 'Current-build banner must remain on README line 3 (inherited W2 gate)'
assert f'vinylstudio-v3-code{code}-r8-mapping' in ci
mappings=set(re.findall(r'V3-code\d+-mapping\.(?:txt|sha256)',build))
assert mappings=={f'V3-code{code}-mapping.txt',f'V3-code{code}-mapping.sha256'}, mappings
phone=(root/'transfer/PHONE_BUILD_AND_RECEIVE.sh').read_text()
receive=(root/'transfer/PHONE_RECEIVE_ONLY.sh').read_text()
verifier=(root/'transfer/VERIFY_PACKAGE.sh').read_text()
assert apk in phone and apk in receive
archive = verify_archive_contract(phone, verifier, receive, apk)
assert 'tests/run-v3-dense-export-gate.sh' in build
assert 'tests/run-v3-dense-export-gate.sh' in ci
assert 'tests/run-v3-dense-export-gate.sh' in gradle
assert 'tests/run-v3-prepared-export-gate.sh' in build
assert 'tests/run-v3-prepared-export-gate.sh' in ci
assert 'tests/run-v3-prepared-export-gate.sh' in gradle
assert 'tests/run-v3-star-cut-safe-gate.sh' in build
assert 'tests/run-v3-star-cut-safe-gate.sh' in ci
assert 'tests/run-v3-beta26-export-matrix-gate.sh' in build
assert 'tests/run-v3-beta26-export-matrix-gate.sh' in ci
assert 'tests/run-v3-beta26-export-matrix-gate.sh' in gradle
assert 'tests/run-v3-style-selection-gate.sh' in gradle
for entry in (build,ci,gradle,(root/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()):
    assert 'tests/run-v3-slider-overprint-repair-gate.sh' in entry, '518 slider/overprint gate missing'
    if code>=527: assert 'tests/run-v3-organic-smoothing-gate.sh' in entry, '527 Organic Flow retention gate missing'
    if code>=525: assert 'tests/run-v3-perceptual-swatch-gate.sh' in entry, '525 perceptual swatch gate missing'
    if code>=523: assert 'tests/run-v3-vector-boolean-gate.sh' in entry, '523 vector Boolean gate missing'
    if code>=522: assert 'tests/run-v3-raster-motif-polish-gate.sh' in entry, '522 raster motif polish gate missing'
    if code>=519: assert 'tests/run-v3-prismatic-weave-gate.sh' in entry, '519 prismatic weave gate missing'
if code>=534:
    for entry in (build,ci,gradle,(root/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()):
        assert 'tests/run-v3-user-guide-gate.sh' in entry, '534 guide gate missing'
if code>=533:
    for entry in (build,ci,gradle,(root/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()):
        assert 'tests/run-v3-portrait-detail-restore-gate.sh' in entry, '533 portrait detail gate missing'
if code>=531:
    for entry in (build,ci,gradle,(root/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()):
        assert 'tests/run-v3-performance-architecture-gate.sh' in entry, '531 performance architecture gate missing'
if code>=530:
    for entry in (build,ci,gradle,(root/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()):
        assert 'tests/run-v3-render-pipeline-gate.sh' in entry, '530 render pipeline gate missing'
        assert 'tests/run-v3-smart-palette-gate.sh' in entry, '529 palette gate missing'
if code>=528:
    assert 'V3RibbonLocalColorTest' in (root/'tests/run-v3-prismatic-weave-gate.sh').read_text(), '528 ribbon local-color gate missing'
if code>=524:
    gate=(root/'tests/run-v3-vector-boolean-gate.sh').read_text()
    assert 'com.halftonestudio.PrismaticVectorBooleanTest' in gate, '524 Prismatic vector gate missing'
assert 'tests/run-v3-style-selection-gate.sh' in build
assert 'tests/run-v3-style-selection-gate.sh' in ci
assert 'tests/run-v3-pop-poster-gate.sh' in gradle
assert 'tests/run-v3-pop-poster-gate.sh' in build
assert 'tests/run-v3-pop-poster-gate.sh' in ci
assert 'tests/run-v3-beta25-snip-mat-gate.sh' in build
assert 'tests/run-v3-beta25-snip-mat-gate.sh' in ci
assert 'tests/run-v3-beta25-snip-mat-gate.sh' in gradle
assert 'tests/run-v3-star-cut-safe-gate.sh' in gradle
assert 'SOURCE_INFO=' in verifier and 'CODE=' in verifier and 'MAPPINGS=' in verifier
assert 'source_part transfer/PHONE_BUILD_AND_RECEIVE.sh' in verifier
assert 'source_part transfer/PHONE_RECEIVE_ONLY.sh' in verifier
assert 'Embedded source and outer TransferKit APK names differ' in verifier
for name in ('README.md','BUILD_ON_NAS.sh','app/build.gradle'):
    assert (root/name).exists()
print(f'PASS current release consistency: v{version} code{code}, R8, CI, README, transfer archive {archive} and APK')

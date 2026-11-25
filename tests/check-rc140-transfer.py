#!/usr/bin/env python3
from pathlib import Path
import json

root=Path(__file__).resolve().parents[1]
common=(root/'transfer/PHONE_COMMON.sh').read_text()
phone=(root/'transfer/PHONE_BUILD_AND_RECEIVE.sh').read_text()
receive=(root/'transfer/PHONE_RECEIVE_ONLY.sh').read_text()
stage=(root/'transfer/NAS_STAGE_BUILD.sh').read_text()
verify=(root/'transfer/VERIFY_PACKAGE.sh').read_text()
meta=json.loads((root/'tests/release-info.json').read_text())

assert 'vinyl_connect(){' in common
assert '-o ControlMaster=yes -Nf' in common
assert 'ControlPath=$VINYL_SOCKDIR/control' in common
assert 'rsync_rsh=' in common and '-o ControlPath=$VINYL_SOCKDIR/control' in common
assert 'vinyl_connect' in phone and phone.index('vinyl_connect')<phone.index('vinyl_send_source')
assert phone.count('vinyl_connect')==1
if meta['gate']=='tests/run-rc165-camouflage-palette-gate.sh':
    assert 'VinylStudio-RC165-CamoPaletteDefaults-source.zip' in phone
    assert meta['apk'] in phone
    assert "SOURCE_DIR='VinylStudio-RC165-CamoPaletteDefaults-source'" in phone
    assert 'camo-palette-defaults-rc165' in stage
elif meta['gate']=='tests/run-rc164-pattern-rebuild-gate.sh':
    assert 'VinylStudio-RC164-PatternRebuild-source.zip' in phone
    assert meta['apk'] in phone
    assert "SOURCE_DIR='VinylStudio-RC164-PatternRebuild-source'" in phone
    assert 'pattern-rebuild-rc164' in stage
elif meta['gate']=='tests/run-rc163-craft-nine-layers-gate.sh':
    assert 'VinylStudio-RC163-CraftNineLayers-source.zip' in phone
    assert meta['apk'] in phone
    assert "SOURCE_DIR='VinylStudio-RC163-CraftNineLayers-source'" in phone
    assert 'craft-nine-layers-rc163' in stage
elif meta['gate']=='tests/run-rc162-render-responsiveness-gate.sh':
    assert 'VinylStudio-RC162-RenderResponsiveness-source.zip' in phone
    assert meta['apk'] in phone
    assert "SOURCE_DIR='VinylStudio-RC162-RenderResponsiveness-source'" in phone
    assert 'render-responsiveness-rc162' in stage
elif meta['gate']=='tests/run-rc161-android-stability-gate.sh':
    assert 'VinylStudio-RC161-AndroidStability-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.26-AndroidStability-RC161.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC161-AndroidStability-source'" in phone
    assert 'android-stability-rc161' in stage
elif meta['gate']=='tests/run-rc160-lambda-gate.sh':
    assert 'VinylStudio-RC160-LambdaCompileFix-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.25-LambdaCompileFix-RC160.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC160-LambdaCompileFix-source'" in phone
    assert 'lambda-compile-rc160' in stage
elif meta['gate']=='tests/run-rc158-confetti-weave-gate.sh':
    assert 'VinylStudio-RC158-ConfettiWeave-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.23-ConfettiWeave-RC158.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC158-ConfettiWeave-source'" in phone
    assert 'confetti-weave-rc158' in stage
elif meta['gate']=='tests/run-rc157-sliders-palette-layers-gate.sh':
    assert 'VinylStudio-RC157-SliderPaletteLayers-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.22-SliderPaletteLayers-RC157.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC157-SliderPaletteLayers-source'" in phone
    assert 'sliders-palette-layers-rc157' in stage
elif meta['gate']=='tests/run-rc156-lint-fix-gate.sh':
    assert 'VinylStudio-RC156-LintFix-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.21-LintFix-RC156.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC156-LintFix-source'" in phone
    assert 'lint-fix-rc156' in stage
elif meta['gate']=='tests/run-rc155-natural-confetti-gate.sh':
    assert 'VinylStudio-RC155-NaturalConfetti-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.20-NaturalConfetti-RC155.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC155-NaturalConfetti-source'" in phone
    assert 'natural-confetti-rc155' in stage
elif meta['gate']=='tests/run-rc154-confetti-stack-gate.sh':
    assert 'VinylStudio-RC154-ConfettiStack-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.19-ConfettiStack-RC154.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC154-ConfettiStack-source'" in phone
    assert 'confetti-stack-rc154' in stage
elif meta['gate']=='tests/run-rc152-three-styles-gate.sh':
    assert 'VinylStudio-RC152-ThreeStyles-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.17-ThreeStyles-RC152.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC152-ThreeStyles-source'" in phone
    assert 'three-styles-rc152' in stage
elif meta['gate']=='tests/run-rc151-thumbnails-gate.sh':
    assert 'VinylStudio-RC151-ThumbnailRefresh-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.16-ThumbnailRefresh-RC151.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC151-ThumbnailRefresh-source'" in phone
    assert 'thumbnail-refresh-rc151' in stage
elif meta['gate']=='tests/run-rc150-prismatic-tone-gate.sh':
    assert 'VinylStudio-RC150-ToneBalance-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.15-PrismaticTone-RC150.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC150-ToneBalance-source'" in phone
    assert 'prismatic-tone-rc150' in stage
elif meta['gate']=='tests/run-rc149-prismatic-gate.sh':
    assert 'VinylStudio-RC149-PrismaticDiagonal-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.14-PrismaticDiagonal-RC149.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC149-PrismaticDiagonal-source'" in phone
    assert 'prismatic-rc149' in stage
elif meta['gate']=='tests/run-rc148-registration-safety-gate.sh':
    assert 'VinylStudio-RC148-RegistrationSafety-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.13-RegistrationSafety-RC148.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC148-RegistrationSafety-source'" in phone
    assert 'registration-rc148' in stage
elif meta['gate']=='tests/run-rc147-registration-viewport-gate.sh':
    assert 'VinylStudio-RC147-RegistrationViewport-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.12-RegistrationViewport-RC147.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC147-RegistrationViewport-source'" in phone
    assert 'registration-rc147' in stage
elif meta['gate']=='tests/run-rc146-reliability-gate.sh':
    assert 'VinylStudio-RC146-QualityConsolidation-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.11-Quality-RC146.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC146-QualityConsolidation-source'" in phone
    assert 'quality-rc146' in stage
elif meta['gate']=='tests/run-rc145-registration-stability-gate.sh':
    assert 'VinylStudio-RC145-RegistrationStability-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.10-Registration-RC145.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC145-RegistrationStability-source'" in phone
    assert 'registration-rc145' in stage
elif meta['gate']=='tests/run-rc144-registration-gate.sh':
    assert 'VinylStudio-RC144-RegistrationRepair-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.9-Registration-RC144.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC144-RegistrationRepair-source'" in phone
    assert 'registration-rc144' in stage
elif meta['gate']=='tests/run-rc143-quality-gate.sh':
    assert 'VinylStudio-RC143-Quality-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.8-Quality-RC143.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC143-Quality-Source'" in phone
    assert 'quality-rc143' in stage
elif meta['gate']=='tests/run-rc142-style-cleanup-gate.sh':
    assert 'VinylStudio-RC142-StyleCleanup-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.7-StyleCleanup-RC142.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC142-StyleCleanup-Source'" in phone
    assert 'style-cleanup-rc142' in stage
elif meta['gate']=='tests/run-rc141-fifteen-styles-gate.sh':
    assert 'VinylStudio-RC141-FifteenStyles-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.6-FifteenStyles-RC141.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC141-FifteenStyles-Source'" in phone
    assert 'fifteen-styles-rc141' in stage
else:
    assert 'VinylStudio-RC140-AssemblyGuide-source.zip' in phone
    assert 'VinylHalftoneStudio-v2.55.5-AssemblyGuide-RC140.apk' in phone
    assert "SOURCE_DIR='VinylStudio-RC140-AssemblyGuide-Source'" in phone
    assert 'assembly-guide-rc140' in stage
assert 'vinyl_connect' in receive
assert 'sha256sum -c SOURCE_SHA256.txt' in verify and 'unzip -tq' in verify
combined='\n'.join((common,phone,receive,stage,verify)).lower()
assert 'sshpass' not in combined and 'password=' not in combined and 'passwd=' not in combined
print('PASS RC140–RC142 one-password multiplexed upload/build/return transfer contract')

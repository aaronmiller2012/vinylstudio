#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
python3 tests/check-rc115-play.py
bash -n BUILD_PLAY_BUNDLE_ON_NAS.sh
bash tests/run-rc114-integrity-gate.sh
# Confirm signing variables are mandatory; test exits before loading SDK/gradle.
if OUTPUT="$(env -u VINYL_RELEASE_KEYSTORE -u VINYL_RELEASE_ALIAS -u VINYL_RELEASE_STORE_PASSWORD -u VINYL_RELEASE_KEY_PASSWORD bash BUILD_PLAY_BUNDLE_ON_NAS.sh 2>&1)"; then
    echo 'FAIL: unsigned Play build unexpectedly succeeded' >&2
    exit 1
fi
[[ "$OUTPUT" == *'VINYL_RELEASE_KEYSTORE'* ]] || { echo 'FAIL: missing-keystore preflight not enforced' >&2; exit 1; }
echo 'PASS RC115 Play static, mandatory signing, and inherited release gates'

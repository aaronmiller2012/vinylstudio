#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
python3 tests/check-v3-fix12-crop-preservation.py
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$TMP" tests/V3CropSnipCutParityTest.java
java -ea -Xmx900m -cp "$TMP" com.halftonestudio.V3CropSnipCutParityTest
printf 'PASS Fix12 crop/Snip physical-cut center-region parity and crop-setting preservation\n'

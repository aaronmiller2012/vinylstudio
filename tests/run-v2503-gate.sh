#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo '===== 240 UNCHANGED WORKING GEOMETRY SIGNATURES ====='
bash tests/run-v2503-known-good-parity.sh
echo '===== ALL 12 LINE FAMILIES: CUT WIDTH, MASKS, SVG, RING SEAMS ====='
bash tests/run-v2503-cut-geometry.sh
echo '===== PER-LAYER CUT COUNTS AND EXPORT SAFETY ====='
bash tests/run-layer-cut-counts.sh
echo '===== SNIP AND JAVA SOURCE SAFETY ====='
bash tests/run-universal-snip.sh
python3 tests/check-v2496-render-pipeline.py
python3 tests/check-stage1-hardening.py
echo 'PASS v2.50.3 mandatory release gate'

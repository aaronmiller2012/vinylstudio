#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo '===== CMYK DEFAULTS, BOUNDED CONTINUITY AND LEGACY PARITY ====='
bash tests/run-v2503-known-good-parity.sh
bash tests/run-v2504-cmyk-continuity.sh
echo '===== ALL STYLE GEOMETRY AND CUT SAFETY ====='
bash tests/run-v2503-cut-geometry.sh
bash tests/run-layer-cut-counts.sh
bash tests/run-universal-snip.sh
python3 tests/check-v2496-render-pipeline.py
python3 tests/check-stage1-hardening.py
python3 tests/check-v2504-ui-wiring.py
echo 'PASS v2.50.4 mandatory release gate'

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo '===== RC83 OFFLINE GUIDE COPY / WIRING CHECK ====='
python3 tests/check-v25449-guide.py
echo '===== INHERITED RC82 INTEGRITY / ARTWORK CHECKS ====='
bash tests/run-v25448-integrity-gate.sh

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2515-gate.sh
python3 tests/check-v2516-stipple-hue-retention.py
printf 'PASS v2.51.6 retained hue and swatches across layer count changes\n'

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC76 PALETTE TAP & SINGLE REVERSE =====\n'
python3 tests/check-v25442-palette-tap.py
python3 tests/check-rc72-release-metadata.py
printf '\n===== RC75 LAYER CRASH, COLOR WHEEL, PHYSICAL STACK =====\n'
bash tests/run-v25441-visible-controls-gate.sh
printf '\nPASS RC76 PALETTE SELECTION GATE\n'

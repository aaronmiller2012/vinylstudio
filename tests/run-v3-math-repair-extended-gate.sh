#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
bash tests/run-v3-math-repair-gate.sh
bash tests/run-v3-overlay-swatch-gate.sh
bash tests/run-v3-fix12-crop-parity-gate.sh
bash tests/run-v3-beta25-snip-mat-gate.sh
bash tests/run-v3-beta26-export-matrix-gate.sh
bash tests/run-rc148-registration-safety-gate.sh
printf 'PASS extended crop, Snip, registration and SVG/DXF matrix gates\n'

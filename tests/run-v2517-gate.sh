#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2516-gate.sh
python3 tests/check-v2517-palette-count-retention.py
python3 tests/check-v2517-compilefix.py
python3 tests/check-v2517-javac-scope.py
printf 'PASS v2.51.7 count-only swatch and hue persistence\n'

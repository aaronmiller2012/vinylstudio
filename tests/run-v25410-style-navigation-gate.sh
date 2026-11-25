#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-v25410-style-navigation.py
bash tests/run-v2549-style-colors-gate.sh

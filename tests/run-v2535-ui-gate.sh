#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-v2535-style-choices.py
python3 tests/check-v2484-style-workspace.py
python3 tests/check-v216-specialty.py
python3 tests/check-v231-editor-polish.py
bash tests/run-v2534-single-line-gate.sh

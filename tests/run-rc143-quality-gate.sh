#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
python3 tests/check-rc143-quality.py
bash tests/run-rc142-style-cleanup-gate.sh

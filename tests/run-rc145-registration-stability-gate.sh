#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
printf '\n===== RC145 REGISTRATION PREVIEW STABILITY GATE =====\n'
python3 tests/check-rc145-registration-stability.py
bash tests/run-rc144-registration-gate.sh
printf '\nPASS RC145: source, stable mark drag, and physical export regressions\n'

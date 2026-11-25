#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Inherit the full known-working v2.52.5 suite (classic/modern CMYK, all-vector, performance).
bash tests/run-v2525-gate.sh
# Validate new UI theme tokens, layout and the exact patch integration.
python3 tests/check-v2526-theme.py
printf 'PASS v2.52.6 RC27 UI makeover and inherited v2.52.5 gates\n'

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Historical line and stipple tests are unchanged; verify once before the build.
bash tests/run-v2508-gate.sh
bash tests/run-v2509-ui-gate.sh
printf 'PASS: v2.50.9 mandatory source compatibility and vector swatch UI gate\n'

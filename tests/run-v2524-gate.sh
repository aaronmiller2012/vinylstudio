#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Historic fixture validates EXACT classic v2.24 and modern fallback vector coordinates.
bash tests/run-v2523-gate.sh
# Real javac with a Path API stub validates actual specialty vector geometry conversion.
bash tests/run-v2524-vector-gate.sh
python3 tests/check-v2524-release.py
printf 'PASS v2.52.4 complete vector canvas release gate\n'

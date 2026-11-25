#!/usr/bin/env bash
# Legacy entrypoint: RC55 retains and strengthens RC54 project library tests.
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v25421-project-safety-gate.sh

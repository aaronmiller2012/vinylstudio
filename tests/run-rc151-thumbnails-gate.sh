#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-rc151-thumbnails.py
python3 tests/check-rc133-thumbnails.py
bash tests/run-rc133-thumbnail-jvm.sh
# The prismatic geometry, CMYK registration and reliability gates remain mandatory.
bash tests/run-rc150-prismatic-tone-gate.sh
printf '\nPASS RC151 refreshed unique thumbnails + RC150/RC148 inherited registration/export tests\n'

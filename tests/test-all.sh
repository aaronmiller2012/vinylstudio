#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-host-tests.sh
printf '%s\n' 'Host-side Java tests passed. Android Gradle build must still be run on a machine with Android SDK.'

python3 tests/check-v216-specialty.py

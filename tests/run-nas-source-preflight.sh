#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-rc173-wrapper-bootstrap.py
# Fast source integration checks before expensive Gradle/JVM work.
python3 tests/check-v3-release-consistency.py
python3 tests/check-rc173-play-readiness.py
python3 tests/check-rc166-style-retirement.py
python3 tests/check-rc173-w5-integration.py
python3 tests/check-v3-integration.py
python3 tests/check-v3-beta3-integration.py

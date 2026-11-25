#!/usr/bin/env bash
set -Eeuo pipefail
python3 "$(dirname "$0")/check-rc173-wrapper-bootstrap.py"
cd "$(dirname "$0")/.."
printf '\n===== RC173 PRIVACY / RELEASE / TRANSFER PREFLIGHT =====\n'
python3 tests/check-rc173-play-readiness.py
python3 tests/check-rc115-play.py
printf '\n===== RC172 / RC171 / RC170 JAVA EXECUTABLE GEOMETRY AND ZIP GATES =====\n'
bash tests/run-rc172-dxf-gate.sh
printf '\nPASS RC173 host gates. Android SDK/Gradle lint/R8/device checks are separate and still mandatory.\n'

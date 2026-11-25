#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP=$(mktemp -d);trap 'rm -rf "$TMP"' EXIT
javac -J-Xmx512m -sourcepath app/src/main/java -d "$TMP" tests/RC85SecurityTest.java
java -Xmx384m -cp "$TMP" com.halftonestudio.RC85SecurityTest
python3 tests/check-v25451-security.py
# Focused tests only. Full history can be run separately using FULL_TESTS=1.

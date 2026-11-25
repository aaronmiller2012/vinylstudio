#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx850m -nowarn -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/V3Fix10GeometryOptionsTest.java
java -ea -Xmx850m -cp "$TMP" com.halftonestudio.V3Fix10GeometryOptionsTest
python3 tests/check-v3-fix10-geometry-options.py

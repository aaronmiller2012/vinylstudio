#!/usr/bin/env bash
# JVM reproduction for Android UI crash on first-time style selection.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java:tests:tests/android-host-stubs -d "$TMP" \
  tests/V3StyleSelectionCrashRegressionTest.java
java -ea -cp "$TMP" com.halftonestudio.V3StyleSelectionCrashRegressionTest

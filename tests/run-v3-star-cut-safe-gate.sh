#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx700m -sourcepath app/src/main/java:tests -d "$TMP" tests/V3StarCutSafeHalftoneTest.java
java -Xmx900m -ea -cp "$TMP" com.halftonestudio.V3StarCutSafeHalftoneTest

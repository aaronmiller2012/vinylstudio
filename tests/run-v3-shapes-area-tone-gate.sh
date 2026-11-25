#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"
trap 'rm -rf -- "$TEMP"' EXIT
javac -J-Xmx700m -sourcepath app/src/main/java:tests -d "$TEMP" tests/V3ShapeAreaToneTest.java
java -Xmx700m -ea -cp "$TEMP" com.halftonestudio.V3ShapeAreaToneTest

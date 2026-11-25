#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf -- "$T"' EXIT
javac -J-Xmx700m -sourcepath app/src/main/java:tests -d "$T" \
    tests/V3SnipContourRepairTest.java tests/V3Beta25SnipMatTest.java
java -ea -Xmx900m -cp "$T" com.halftonestudio.V3SnipContourRepairTest
java -ea -Xmx900m -cp "$T" com.halftonestudio.V3Beta25SnipMatTest

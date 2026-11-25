#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$T" tests/V3RenderSpeedParityTest.java
java -ea -Xmx1200m -cp "$T" com.halftonestudio.V3RenderSpeedParityTest
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$T" tests/V3RenderSpeedHardeningTest.java
java -ea -Xmx1000m -cp "$T" com.halftonestudio.V3RenderSpeedHardeningTest


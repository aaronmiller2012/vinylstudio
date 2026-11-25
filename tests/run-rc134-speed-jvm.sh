#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "$0")/.."
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
javac -J-Xmx512m -sourcepath app/src/main/java:tests -d "$classes" \
 tests/RC134RenderSpeedHostTest.java \
 app/src/main/java/com/halftonestudio/SnipMask.java \
 app/src/main/java/com/halftonestudio/RenderDraftSourceCache.java
java -Xmx512m -cp "$classes" com.halftonestudio.RC134RenderSpeedHostTest

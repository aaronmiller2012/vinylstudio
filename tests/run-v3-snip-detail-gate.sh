#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$TMP" tests/V3SnipPortraitDetailTest.java
java -ea -Xmx850m -cp "$TMP" com.halftonestudio.V3SnipPortraitDetailTest

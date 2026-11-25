#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$TMP" tests/V3MathRepairTest.java
java -ea -Xmx950m -cp "$TMP" com.halftonestudio.V3MathRepairTest
# Catch accidental Java syntax errors even without the Android SDK.
javac -sourcepath app/src/main/java -d "$TMP" tests/ParseJavaSource.java
java -cp "$TMP" ParseJavaSource app/src/main/java/com/halftonestudio/PhotoCropEditor.java app/src/main/java/com/halftonestudio/StudioActivity.java
python3 tests/check-rc146-wiring.py
printf 'PASS V3 Wolfram math repair, Android-source syntax and staged-export flow gate\n'

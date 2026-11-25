#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"; trap 'rm -rf "$TEMP"' EXIT
javac -sourcepath app/src/main/java -d "$TEMP" app/src/main/java/com/halftonestudio/SnipInputPolicy.java tests/SnipInputPolicyTest.java
java -cp "$TEMP" com.halftonestudio.SnipInputPolicyTest
python3 tests/check-v2493-universal-snip.py
javac -sourcepath app/src/main/java -d "$TEMP" tests/JavaSyntaxCheck.java
java -cp "$TEMP" com.halftonestudio.JavaSyntaxCheck app/src/main/java/com/halftonestudio/StudioActivity.java

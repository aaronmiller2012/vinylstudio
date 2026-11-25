#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
printf '\n=== RC86: portable project source + compatibility ===\n'
javac -J-Xmx512m -sourcepath app/src/main/java -d "$TMP" tests/RC86StabilityTest.java
java -Xmx512m -cp "$TMP" com.halftonestudio.RC86StabilityTest
python3 tests/check-v25452-stability.py
printf '\n=== All Java source syntax checks (SDK-free parse) ===\n'
javac -d "$TMP" tests/JavaSyntaxCheck.java
mapfile -t ALL_JAVA < <(find app/src/main/java -name '*.java' -type f | sort)
java -Xmx512m -cp "$TMP" com.halftonestudio.JavaSyntaxCheck "${ALL_JAVA[@]}"
printf '\n=== RC85: security behavior and safeguards ===\n'
bash tests/run-v25451-security-gate.sh
printf '\n=== RC84: photo aware / TSP / gamma parity ===\n'
javac -J-Xmx768m -sourcepath app/src/main/java -d "$TMP" tests/ImageLevelsReference.java tests/RC84PerformanceTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.RC84PerformanceTest
printf '\n=== Historical line tests explicitly select MODERN branch ===\n'
bash tests/run-v2504-cmyk-continuity.sh
bash tests/run-v2505-portrait-recovery.sh
javac -J-Xmx768m -sourcepath app/src/main/java -d "$TMP" app/src/main/java/com/halftonestudio/CmykPortraitDefaults.java tests/ScreenAndVinylBalanceTest.java
java -Xmx512m -cp "$TMP" com.halftonestudio.ScreenAndVinylBalanceTest
printf '\nPASS RC86 mandatory source/behavior release gate\n'

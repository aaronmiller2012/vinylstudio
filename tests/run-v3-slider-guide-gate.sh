#!/usr/bin/env bash
# Every adjustment slider has its own detailed ⓘ explanation (SliderGuide).
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
python3 tests/check-gate-manifest.py
javac -J-Xmx600m -nowarn -sourcepath app/src/main/java:tests -d "$T" tests/V3SliderGuideTest.java tests/CutIntelligenceTest.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -cp "$T" com.halftonestudio.V3SliderGuideTest
java -ea -cp "$T" com.halftonestudio.CutIntelligenceTest
javac -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/{SliderGuide,ContextualLearning}.java
printf 'PASS slider guide gate\n'

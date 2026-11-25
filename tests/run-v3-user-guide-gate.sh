#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-v25449-guide.py
python3 tests/check-v2494-quick-start.py
python3 tests/check-v3-fix14-tonal-ui.py
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java:tests -d "$T" tests/{VinylGuidePolicyTest,ParseJavaSource,OutputDeviceProfileTest,V3Beta25SnipMatTest}.java
java -cp "$T" com.halftonestudio.VinylGuidePolicyTest
java -cp "$T" com.halftonestudio.OutputDeviceProfileTest
java -cp "$T" com.halftonestudio.V3Beta25SnipMatTest
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/{VinylQuickStartGuide,StudioActivity}.java
javac -sourcepath app/src/main/java:tests -d "$T" tests/StyleDiscoveryTest.java
java -cp "$T" com.halftonestudio.StyleDiscoveryTest
python3 tests/check-rc129-picker-scopes.py
javac -sourcepath app/src/main/java:tests -d "$T" tests/CutIntelligenceTest.java
java -cp "$T" com.halftonestudio.CutIntelligenceTest
javac -sourcepath app/src/main/java:tests -d "$T" tests/ParallelWidthCandidatesTest.java
java -cp "$T" com.halftonestudio.ParallelWidthCandidatesTest

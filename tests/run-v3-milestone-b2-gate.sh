#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf -- "$T"' EXIT
javac -J-Xmx700m -sourcepath app/src/main/java:tests -d "$T" tests/V3MilestoneB2ToneTest.java
java -Xmx700m -ea -cp "$T" com.halftonestudio.V3MilestoneB2ToneTest
python3 tests/check-v3-milestone-b2-wiring.py

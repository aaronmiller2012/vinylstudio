#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
javac -J-Xmx700m -nowarn -sourcepath app/src/main/java:tests -d "$T" \
    tests/{V3M3DamagedLibraryTest,StudioLibraryJobsTest,StudioProjectLibraryTest,LibraryDocumentTruncationTest,V3M3CorruptionResilienceTest}.java
java -ea -Xmx768m -cp "$T" com.halftonestudio.V3M3DamagedLibraryTest
java -ea -Xmx768m -cp "$T" com.halftonestudio.StudioProjectLibraryTest
java -ea -Xmx768m -cp "$T" com.halftonestudio.StudioLibraryJobsTest
java -ea -Xmx768m -cp "$T" com.halftonestudio.LibraryDocumentTruncationTest
java -ea -Xmx768m -cp "$T" com.halftonestudio.V3M3CorruptionResilienceTest
python3 tests/check-rc170-integration.py

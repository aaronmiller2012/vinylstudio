#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC56 Architecture wiring =====\n'
python3 tests/check-v25422-architecture.py
# VinylEngine changes intentionally in RC63. Preserve and verify all *other*
# original RC56 hashes; RC63 has a dedicated engine behavior + geometry gate.
grep -v '  app/src/main/java/com/halftonestudio/VinylEngine.java$' tests/v25422-inherited-source.sha256 | sha256sum -c -
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
printf '\n===== Session + repository jobs Java tests =====\n'
javac -sourcepath app/src/main/java -d "$T" "$D"/{StudioProjectSessionState,StudioProjectLibrary,StudioProjectBackup,StudioProjectArchiveWriter,ProjectArchiveReader,StudioLibraryJobs}.java tests/{StudioProjectSessionStateTest,StudioLibraryJobsTest}.java
java -ea -cp "$T" com.halftonestudio.StudioProjectSessionStateTest
java -ea -cp "$T" com.halftonestudio.StudioLibraryJobsTest
printf '\n===== Android source syntax =====\n'
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D"/StudioActivity.java "$D"/StudioProjectThumbnails.java "$D"/StudioLibraryJobs.java "$D"/StudioProjectSessionState.java
printf '\n===== RC55 storage and inherited engine gates =====\n'
javac -sourcepath app/src/main/java -d "$T" "$D"/{StudioProjectLibrary,StudioProjectBackup,StudioProjectArchiveWriter,ProjectArchiveReader}.java tests/StudioProjectLibraryTest.java
java -ea -cp "$T" com.halftonestudio.StudioProjectLibraryTest
bash tests/run-v25419-cleanup-gate.sh
printf '\nPASS RC56 architecture extraction with inherited tests\n'

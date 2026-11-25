#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC57 integration and inherited source hashes =====\n'
# RC57-specific version identity differs in RC58: assert the retained optimizations directly.
python3 tests/check-v25423-performance-inherited.py
python3 tests/test_profile_summary.py
# VinylEngine changes intentionally in RC63. Preserve and verify all *other*
# original RC56 hashes; RC63 has a dedicated engine behavior + geometry gate.
# RC68 updated these engines to share physical registration layouts. Check the
# newer, frozen RC68 engine hashes rather than obsolete RC56 expectations.
if [[ -f tests/v25474-preserved-engines.sha256 ]]; then
  grep -Ev '  app/src/main/java/com/halftonestudio/(VinylEngine|TessellationEngine|LayeredCellPortraitEngine|MultilayerStencilEngine).java$' \
    tests/v25422-inherited-source.sha256 | sha256sum -c -
  # These four files are unchanged since the user-visible RC74 release.
  sha256sum -c tests/v25474-preserved-engines.sha256
elif [[ -f tests/v25434-registration-engine-baseline.sha256 ]]; then
  grep -Ev '  app/src/main/java/com/halftonestudio/(VinylEngine|TessellationEngine|LayeredCellPortraitEngine|MultilayerStencilEngine).java$' \
    tests/v25422-inherited-source.sha256 | sha256sum -c -
  sha256sum -c tests/v25434-registration-engine-baseline.sha256
else
  grep -v '  app/src/main/java/com/halftonestudio/VinylEngine.java$' tests/v25422-inherited-source.sha256 | sha256sum -c -
fi
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
printf '\n===== RC57 pure Java memory and cancellation tests =====\n'
javac -sourcepath app/src/main/java -d "$T" "$D"/BoundedTonalPreview.java "$D"/StudioRenderController.java tests/BoundedTonalPreviewTest.java tests/StudioRenderControllerTest.java
java -ea -cp "$T" com.halftonestudio.BoundedTonalPreviewTest
java -ea -cp "$T" com.halftonestudio.StudioRenderControllerTest
printf '\n===== Modified Android source syntax =====\n'
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D"/StudioActivity.java "$D"/StudioProjectThumbnails.java "$D"/BoundedTonalPreview.java
printf '\n===== RC56 architecture and inherited project-library gates =====\n'
# RC56 source version assertion is specific to RC56; run its Java/engine tests
# without asserting that the new release has the old versionCode.
javac -sourcepath app/src/main/java -d "$T" "$D"/{StudioProjectSessionState,StudioProjectLibrary,StudioProjectBackup,StudioProjectArchiveWriter,ProjectArchiveReader,StudioLibraryJobs}.java tests/{StudioProjectSessionStateTest,StudioLibraryJobsTest,StudioProjectLibraryTest}.java
java -ea -cp "$T" com.halftonestudio.StudioProjectSessionStateTest
java -ea -cp "$T" com.halftonestudio.StudioLibraryJobsTest
java -ea -cp "$T" com.halftonestudio.StudioProjectLibraryTest
bash tests/run-v25419-cleanup-gate.sh
printf '\nPASS RC57 performance gate\n'

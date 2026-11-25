#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-current-release.py
bash tests/run-rc173-w2-host-gate.sh
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx700m -sourcepath app/src/main/java -d "$TMP" \
  app/src/main/java/com/halftonestudio/{SvgDxfR13Exporter,PhotoPersistenceQueue,ProjectImageSignature,ProjectEncodedOriginal,ProjectArchiveReader,StudioProjectLibrary,StudioProjectArchiveWriter,JsonSyntaxVerifier,ThumbnailHeader,ProjectManifest,EngineBuild,SharedExportFiles}.java \
  tests/RC173W4CorrectnessTest.java
java -Xmx700m -ea -cp "$TMP" com.halftonestudio.RC173W4CorrectnessTest
printf '\nPASS RC173-W4 inherited gates plus executable export, project and persistence tests\n'

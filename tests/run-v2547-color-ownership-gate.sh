#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" "$D"/{CancellationCheckpoint,RegistrationMarks,MultiColorStippleEngine,TessellationEngine,PhotoCellImportance}.java \
  tests/{PhotoColorOwnershipV2547Test,TessellationEngineTest}.java
java -Xmx512m -cp "$T" com.halftonestudio.PhotoColorOwnershipV2547Test
java -Xmx512m -cp "$T" com.halftonestudio.TessellationEngineTest
bash tests/run-v2546-shared-boundary-gate.sh
python3 tests/check-v2547-color-release.py

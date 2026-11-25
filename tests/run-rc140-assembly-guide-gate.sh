#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
python3 tests/check-rc140-assembly-guide.py
python3 tests/check-rc140-transfer.py
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
compile_java(){
  if command -v javac >/dev/null 2>&1; then javac -J-Xmx256m "$@"
  else java -Xmx256m -m jdk.compiler/com.sun.tools.javac.Main "$@"
  fi
}
compile_java -sourcepath app/src/main/java:tests -d "$TEMP" tests/AssemblyGuidePlanHostTest.java
java -cp "$TEMP" com.halftonestudio.AssemblyGuidePlanHostTest
compile_java -sourcepath tests -d "$TEMP" tests/JavaSyntaxCheck.java
java -cp "$TEMP" com.halftonestudio.JavaSyntaxCheck \
  app/src/main/java/com/halftonestudio/StudioActivity.java \
  app/src/main/java/com/halftonestudio/ArtworkDocumentExport.java \
  app/src/main/java/com/halftonestudio/AssemblyGuideDocument.java \
  app/src/main/java/com/halftonestudio/AssemblyGuidePlan.java
bash tests/run-rc138-crop-lines-gate.sh
printf '\nPASS RC140 assembly-guide release gate\n'

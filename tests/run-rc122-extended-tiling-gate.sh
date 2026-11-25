#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
printf '\n===== RC122: EXTENDED TILING GEOMETRY =====\n'
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$TMP" tests/ExtendedTessellationHostTest.java
java -Xmx900m -cp "$TMP" com.halftonestudio.ExtendedTessellationHostTest
bash tests/run-rc121-tessellation-gate.sh
printf '\nPASS RC122 extended tessellation release gate\n'

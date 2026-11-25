#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"; trap 'rm -rf -- "$OUT"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$OUT" tests/GoldenGeometryHostTest.java
# Run only after reviewing the proposed algorithm change and comparing old diffs.
java -Xmx900m -cp "$OUT" com.halftonestudio.GoldenGeometryHostTest --bless

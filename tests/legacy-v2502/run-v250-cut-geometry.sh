#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP" app/src/main/java/com/halftonestudio/{SnipMask,RegistrationMarks,CmykWhitePaperBalance,LineRibbonPolicy,LineArtEngine,StencilSeparationEngine,VinylEngine,LineCutDiagnostics}.java tests/{LineArtEngineTest,LineCutReleaseTest}.java
java -Xmx512m -cp "$TMP" com.halftonestudio.LineArtEngineTest
java -Xmx512m -cp "$TMP" com.halftonestudio.LineCutReleaseTest
python3 tests/check-v250-cut-geometry.py

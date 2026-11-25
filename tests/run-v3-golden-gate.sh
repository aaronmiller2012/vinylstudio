#!/usr/bin/env bash
# Build 554: golden cut-sheet signatures + render-time budgets for every selectable style.
# Re-record after an intended style change: VINYL_UPDATE_GOLDEN=1 bash tests/run-v3-golden-gate.sh
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -J-Xmx750m -nowarn -sourcepath app/src/main/java:tests -d "$T" tests/{V3StyleGoldenTest,V3AllStyleExportMatrixTest}.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx900m -cp "$T" com.halftonestudio.V3StyleGoldenTest

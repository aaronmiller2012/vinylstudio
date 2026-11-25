#!/usr/bin/env bash
# ADR-0001 export sheet contract (real engines + real ZIP writer), Cricut mat requirement,
# selected-mat enforcement on real marked sheets (M5), and D1 autosave quarantine. See docs/DECISIONS/0001-export-sheet-contract.md.
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
python3 tests/check-gate-manifest.py
javac -J-Xmx700m -nowarn -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
  tests/V3ExportSheetContractTest.java tests/SuiteExportCall.java tests/V3AllStyleExportMatrixTest.java \
  tests/V3CricutMatRequirementTest.java tests/MatFitQualificationTest.java tests/EditorAutosaveRecoveryTest.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx1g -cp "$T" com.halftonestudio.EditorAutosaveRecoveryTest
java -ea -Xmx1g -cp "$T" com.halftonestudio.V3CricutMatRequirementTest
java -ea -Xmx1g -cp "$T" com.halftonestudio.MatFitQualificationTest
java -ea -Xmx1g -cp "$T" com.halftonestudio.V3ExportSheetContractTest
printf 'PASS export sheet contract gate\n'

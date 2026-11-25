#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx700m -nowarn -sourcepath app/src/main/java:tests -d "$TMP" tests/V3Fix8AuditRepairTest.java
java -ea -Xmx700m -cp "$TMP" com.halftonestudio.V3Fix8AuditRepairTest

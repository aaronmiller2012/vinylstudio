#!/usr/bin/env bash
# M5 line engines: error-diffused highlight dashes (Banknote Engraving), the dash modulator's
# geometric properties, and a slider audit proving every visible line-style control changes
# the production cut geometry. Real engines, real photo (tests/m4/photos).
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
printf '\n===== M5 LINES: dash diffusion + slider audit =====\n'
javac -J-Xmx700m -nowarn -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
    tests/{M5DashModulatorTest,M5BanknoteDashTest,M5LineSliderEffectTest,M5LineDiffusionBench,M4ArtBench}.java \
    2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx256m  -cp "$T" com.halftonestudio.M5DashModulatorTest
java -ea -Xmx1200m -Djava.awt.headless=true -cp "$T" com.halftonestudio.M5BanknoteDashTest
java -ea -Xmx1g    -Djava.awt.headless=true -cp "$T" com.halftonestudio.M5LineSliderEffectTest
printf '\nPASS M5 lines gate\n'

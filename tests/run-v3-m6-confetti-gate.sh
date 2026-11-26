#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
javac -J-Xmx750m -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" tests/{M6ConfettiDetailTest,M6ConfettiVectorTest,M6ConfettiRepairTest,M4ArtBench,M5Fixtures,V3AllStyleExportMatrixTest,GeosVectorReferenceBackend,FallenConfettiRC155Test,ConfettiToneRC154Test,ConfettiStackRC154Test,ConfettiWeaveRC158Test,M5ConfettiBaseTest,M5PhotoSubsetTest,V3PerceptualSwatchRepairTest}.java
for test in FallenConfettiRC155Test ConfettiToneRC154Test ConfettiStackRC154Test ConfettiWeaveRC158Test V3PerceptualSwatchRepairTest M5ConfettiBaseTest M6ConfettiDetailTest; do
    java -ea -Xmx850m -Djava.awt.headless=true -cp "$T" "com.halftonestudio.$test"
done
java -ea -Xmx500m -cp "$T" com.halftonestudio.M6ConfettiRepairTest --policy
# Exact production vector backend, same pinned dependency as other mandatory gates.
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.locationtech.jts/jts-core/1.20.0"
JAR="${VINYL_JTS_JAR:-}"
if [[ -z "$JAR" && -d "$CACHE" ]]; then JAR="$(find "$CACHE" -type f -name jts-core-1.20.0.jar -print -quit)"; fi
if [[ -z "$JAR" ]]; then
    JAR="$T/jts-core-1.20.0.jar"
    curl -fL --retry 2 --connect-timeout 15 --max-time 120 'https://repo.maven.apache.org/maven2/org/locationtech/jts/jts-core/1.20.0/jts-core-1.20.0.jar' -o "$JAR"
fi
printf '%s  %s\n' '6a783d8f9dba3d3cf7265435f134402f63c05838aa6cbcc4297ad3a5b2842baf' "$JAR" | sha256sum -c -
javac -J-Xmx750m -cp "$T:$JAR" -sourcepath app/src/main/java -d "$T" "$D/JtsVectorBooleanBackend.java"
java -ea -Xmx950m -Djava.awt.headless=true -cp "$T:$JAR" com.halftonestudio.M6ConfettiVectorTest
java -ea -Xmx950m -Djava.awt.headless=true -cp "$T:$JAR" com.halftonestudio.M6ConfettiRepairTest
printf '\nPASS M6 Confetti detail and vector export/review contract (not physical cut qualification)\n'

#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC114 bounded jobs, rollback, and transfer security gate =====\n'
bash tests/run-rc110-export-gate.sh
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx512m -sourcepath app/src/main/java:tests -d "$TMP" \
    tests/StudioLibraryJobsTest.java tests/RC114LibraryQueueHostTest.java
java -Xmx512m -cp "$TMP" com.halftonestudio.StudioLibraryJobsTest
java -Xmx512m -cp "$TMP" com.halftonestudio.RC114LibraryQueueHostTest
python3 tests/check-rc113-rollback.py
python3 tests/check-rc114-integrity.py
bash tests/rc114-transfer-mock.sh
for script in BUILD_ON_NAS.sh transfer/PHONE_COMMON.sh transfer/PHONE_BUILD_AND_RECEIVE.sh \
    transfer/PHONE_RECEIVE_ONLY.sh transfer/NAS_STAGE_BUILD.sh; do
    bash -n "$script"
done
printf '\nPASS RC114 mandatory integrity + compatibility gate\n'

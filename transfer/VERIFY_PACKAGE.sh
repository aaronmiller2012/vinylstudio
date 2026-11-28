#!/data/data/com.termux/files/usr/bin/bash
# Portable phone preflight: derive release code/filename from the exact embedded
# source, instead of freezing metadata from the previous beta. No SSH here.
set -Eeuo pipefail
BASE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ARCHIVE='VinylStudio-V3-Product569-source.zip'
for file in "$ARCHIVE" SOURCE_SHA256.txt PHONE_COMMON.sh PHONE_BUILD_AND_RECEIVE.sh PHONE_RECEIVE_ONLY.sh NAS_STAGE_BUILD.sh; do
    [[ -s "$BASE/$file" ]] || { echo "Missing TransferKit file: $file" >&2; exit 14; }
done
expected="$(awk -v name="$ARCHIVE" '$2 == name && NF == 2 { print $1 }' "$BASE/SOURCE_SHA256.txt")"
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo 'Malformed source SHA256 manifest' >&2; exit 14; }
[[ "$(wc -l < "$BASE/SOURCE_SHA256.txt" | tr -d ' ')" == '1' ]] || { echo 'SOURCE_SHA256.txt must contain exactly one entry' >&2; exit 14; }
(cd "$BASE" && sha256sum -c -- SOURCE_SHA256.txt) || { echo 'Source ZIP checksum mismatch' >&2; exit 14; }
unzip -tq "$BASE/$ARCHIVE" >/dev/null || { echo 'Source ZIP damaged' >&2; exit 14; }
source_part(){ unzip -p "$BASE/$ARCHIVE" "VinylStudio-V3-source/$1"; }
SOURCE_INFO="$(source_part tests/release-info.json)" || { echo 'Missing release metadata' >&2; exit 14; }
CODE="$(printf '%s\n' "$SOURCE_INFO" | sed -nE 's/^[[:space:]]*"versionCode":[[:space:]]*([0-9]+),?.*/\1/p')"
APK="$(printf '%s\n' "$SOURCE_INFO" | sed -nE 's/^[[:space:]]*"apk":[[:space:]]*"([^"]+)",?.*/\1/p')"
NAME="$(printf '%s\n' "$SOURCE_INFO" | sed -nE 's/^[[:space:]]*"versionName":[[:space:]]*"([^"]+)",?.*/\1/p')"
[[ "$CODE" =~ ^[0-9]+$ && "$NAME" =~ ^3\.[0-9]+\.[0-9]+$ && "$APK" == "VinylHalftoneStudio-v${NAME}-"*.apk && "$APK" =~ ^VinylHalftoneStudio-v[0-9.]+-[A-Za-z0-9._-]+\.apk$ ]] || {
    echo 'Malformed embedded release metadata' >&2; exit 14;
}
NAS_BUILD_SCRIPT="$(source_part BUILD_ON_NAS.sh)" || { echo 'Missing NAS build script' >&2; exit 14; }
GRADLE="$(source_part app/build.gradle)" || { echo 'Missing Android build file' >&2; exit 14; }
README_EMBEDDED="$(source_part README.md)" || { echo 'Missing source README' >&2; exit 14; }
CI="$(source_part .github/workflows/verify.yml)" || { echo 'Missing source CI' >&2; exit 14; }
EXPECTED_MAPPING="V3-code${CODE}-mapping.txt"
EXPECTED_DIGEST="V3-code${CODE}-mapping.sha256"
[[ "$GRADLE" == *"versionCode $CODE;"* && "$GRADLE" == *"versionName '$NAME'"* ]] || { echo 'Gradle version mismatch' >&2; exit 14; }
[[ "$NAS_BUILD_SCRIPT" == *"release-artifacts/$EXPECTED_MAPPING"* && "$NAS_BUILD_SCRIPT" == *"release-artifacts/$EXPECTED_DIGEST"* ]] || {
    echo "R8 mapping does not match build $CODE" >&2; exit 14;
}
# Reject any *other* active R8 mapping identifier, not only the immediately
# preceding release's code. A new mismatch must fail before prompting for SSH.
MAPPINGS="$(printf '%s\n' "$NAS_BUILD_SCRIPT" | grep -oE 'V3-code[0-9]+-mapping\.(txt|sha256)' | sort -u)"
[[ "$MAPPINGS" == "$(printf '%s\n%s' "$EXPECTED_DIGEST" "$EXPECTED_MAPPING" | sort -u)" ]] || {
    echo "Conflicting R8 mapping names in source: $MAPPINGS" >&2; exit 14;
}
# W2 hardening expects the banner on line THREE, not merely anywhere in the README.
# Reject misplaced banners before any NAS SSH connection or lengthy test suite.
BANNER="$(printf '%s\n' "$README_EMBEDDED" | sed -n '3p')"
[[ "$BANNER" == "**Current build:** versionName $NAME, versionCode $CODE, APK \`$APK\`." ]] || {
    echo 'Embedded README current-build banner must be on line 3' >&2; exit 14;
}
[[ "$CI" == *"code${CODE}-r8-mapping"* ]] || { echo 'CI mapping artifact does not match build' >&2; exit 14; }
[[ "$NAS_BUILD_SCRIPT" == *"$APK"* ]] || { echo 'NAS output APK name mismatch' >&2; exit 14; }
# The outer TransferKit can be correct while the embedded source-side phone
# scripts still name an older release. Fail before SSH/NAS build in that case.
INNER_PHONE="$(source_part transfer/PHONE_BUILD_AND_RECEIVE.sh)" || { echo 'Embedded phone build script missing' >&2; exit 14; }
INNER_RECEIVE="$(source_part transfer/PHONE_RECEIVE_ONLY.sh)" || { echo 'Embedded receive script missing' >&2; exit 14; }
INNER_VERIFY="$(source_part transfer/VERIFY_PACKAGE.sh)" || { echo 'Embedded verifier missing' >&2; exit 14; }
[[ "$(grep -Fxc "ARCHIVE='$ARCHIVE'" <<< "$INNER_PHONE")" == 1 &&
   "$(grep -Fxc "ARCHIVE='$ARCHIVE'" <<< "$INNER_VERIFY")" == 1 ]] || {
   echo 'Embedded source and outer TransferKit archive names differ' >&2; exit 14;
}
[[ "$(grep -Fxc "APK='$APK'" <<< "$INNER_PHONE")" == 1 &&
   "$(grep -Fxc "APK='$APK'" <<< "$INNER_RECEIVE")" == 1 ]] || {
   echo 'Embedded source and outer TransferKit APK names differ' >&2; exit 14;
}
for script in PHONE_COMMON.sh PHONE_BUILD_AND_RECEIVE.sh PHONE_RECEIVE_ONLY.sh NAS_STAGE_BUILD.sh; do
    bash -n "$BASE/$script" || { echo "Invalid shell script: $script" >&2; exit 14; }
done
bash -n "$BASE/VERIFY_PACKAGE.sh" || exit 14
grep -Fq "$ARCHIVE" "$BASE/PHONE_BUILD_AND_RECEIVE.sh" || { echo 'Phone source name mismatch' >&2; exit 14; }
grep -Fq "$APK" "$BASE/PHONE_BUILD_AND_RECEIVE.sh" || { echo 'Phone APK name mismatch' >&2; exit 14; }
grep -Fq "$APK" "$BASE/PHONE_RECEIVE_ONLY.sh" || { echo 'Receive-only APK mismatch' >&2; exit 14; }
printf 'PASS V3 Precision: SHA-256, build %s, README, Gradle, R8, CI, phone/NAS metadata\n' "$CODE"

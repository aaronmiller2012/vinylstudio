#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
BASE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$BASE/PHONE_COMMON.sh"
ARCHIVE='VinylStudio-V3-Product565-source.zip'
APK='VinylHalftoneStudio-v3.5.4-Product565.apk'
SOURCE_DIR='VinylStudio-V3-source'
vinyl_init 'build-and-receive'
# Fail locally before prompting for a NAS password.
bash "$BASE/VERIFY_PACKAGE.sh" || vinyl_fail 14 'TransferKit failed local integrity preflight'
vinyl_connect
for dep in unzip sha256sum; do command -v "$dep" >/dev/null || vinyl_fail 12 "Missing $dep"; done
[[ -f "$BASE/NAS_STAGE_BUILD.sh" ]] || vinyl_fail 14 'Missing NAS_STAGE_BUILD.sh'
EXPECTED="$(awk -v name="$ARCHIVE" '$2==name {print $1}' "$BASE/SOURCE_SHA256.txt")"
[[ "$EXPECTED" =~ ^[0-9a-f]{64}$ ]] || vinyl_fail 14 'Malformed source SHA256 manifest'
vinyl_ssh 'mkdir -p "$HOME/Downloads" "$HOME/VinylStudio-builds"' || vinyl_fail 20 'NAS unreachable or permission denied (check SSH/Tailscale)'
vinyl_send_source "$BASE" "$ARCHIVE" "$EXPECTED"
printf '\n[NAS] Preflight, isolated build, and atomic publication\n'
vinyl_ssh bash -s -- "$ARCHIVE" "$EXPECTED" "$SOURCE_DIR" "$APK" < "$BASE/NAS_STAGE_BUILD.sh" || vinyl_fail 40 'NAS build failed. Previous source and APK remain unchanged; check log.'
vinyl_receive_apk "$APK"

#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
BASE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$BASE/PHONE_COMMON.sh"
APK='VinylHalftoneStudio-v3.5.7-Product568.apk'
vinyl_init 'receive-only'
vinyl_connect
vinyl_receive_apk "$APK"

#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
BASE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$BASE/PHONE_COMMON.sh"
APK='VinylHalftoneStudio-v3.5.4-Product565.apk'
vinyl_init 'receive-only'
vinyl_connect
vinyl_receive_apk "$APK"

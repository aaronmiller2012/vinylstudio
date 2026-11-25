#!/usr/bin/env bash
# RC173-W1: obtain an authenticated Gradle wrapper bootstrap JAR.
# IMPORTANT: Gradle publishes the .jar.sha256 file at services.gradle.org,
# but the corresponding .jar URL returns 404. Use the official Git tag.
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
W="$ROOT/gradle/wrapper/gradle-wrapper.jar"
SHA='2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'
URL='https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar'
mkdir -p -- "$(dirname -- "$W")"
check() {
  local actual
  [[ -s "$1" ]] || return 1
  actual="$(sha256sum "$1" | awk '{print $1}')"
  [[ "$actual" == "$SHA" ]]
}
if [[ -e "$W" ]]; then
  check "$W" || { echo 'ERROR: existing Gradle wrapper JAR fails official SHA-256 verification. Refusing execution.' >&2; exit 29; }
  echo 'Gradle wrapper JAR: official SHA-256 OK (already present).'
  exit 0
fi
# Optional offline reuse: the file is accepted ONLY after matching the
# official SHA-256. Never execute/copy a user-supplied JAR without verifying.
if [[ -n "${VINYL_GRADLE_WRAPPER_JAR:-}" ]]; then
  check "$VINYL_GRADLE_WRAPPER_JAR" || { echo 'ERROR: VINYL_GRADLE_WRAPPER_JAR checksum invalid.' >&2; exit 29; }
  cp -- "$VINYL_GRADLE_WRAPPER_JAR" "$W"
  check "$W" || { echo 'ERROR: copied wrapper did not verify.' >&2; rm -f -- "$W"; exit 29; }
  echo 'Gradle wrapper JAR: verified offline JAR installed.'
  exit 0
fi
command -v curl >/dev/null 2>&1 || { echo 'ERROR: curl is required for verified Gradle wrapper bootstrap.' >&2; exit 12; }
TMP="$(mktemp "$(dirname -- "$W")/.gradle-wrapper.XXXXXXXX")"
trap 'rm -f -- "$TMP"' EXIT
printf 'Downloading official Gradle 8.11.1 wrapper JAR from Gradle GitHub tag...\n'
if ! curl --fail --silent --show-error --location \
        --retry 2 --connect-timeout 15 --max-time 120 \
        --proto '=https' --proto-redir '=https' \
        --output "$TMP" "$URL"; then
  echo "ERROR: wrapper download failed: $URL" >&2
  echo 'If GitHub is blocked, supply a locally cached, verified wrapper JAR through VINYL_GRADLE_WRAPPER_JAR.' >&2
  exit 22
fi
if ! check "$TMP"; then
  printf 'ERROR: downloaded wrapper JAR did not match official SHA-256 %s. Refusing execution.\n' "$SHA" >&2
  exit 29
fi
mv -f -- "$TMP" "$W"
trap - EXIT
printf 'Gradle wrapper JAR authenticated: %s\n' "$SHA"

#!/usr/bin/env bash
# Executed through SSH on the NAS. Never edits prior successful source revisions.
set -Eeuo pipefail
(( $# == 4 )) || { echo 'NAS build expects archive, hash, source folder, APK' >&2; exit 2; }
ARCHIVE="$1"; EXPECTED="$2"; SRC_DIR="$3"; APK="$4"
for item in "$ARCHIVE" "$SRC_DIR" "$APK"; do
    [[ "$item" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Invalid release identifier" >&2; exit 12; }
done
[[ "$EXPECTED" =~ ^[0-9a-f]{64}$ ]] || { echo 'Malformed expected hash' >&2; exit 12; }
ROOT="${VINYL_NAS_BUILD_ROOT:-$HOME/VinylStudio-builds}"
mkdir -p "$ROOT/releases" "$ROOT/logs" "$HOME/Downloads"
LOG="$ROOT/logs/build-$(date +%Y%m%d-%H%M%S)-$$.log"
exec > >(tee -a "$LOG") 2>&1
printf '[NAS] log: %s\n' "$LOG"
for dep in unzip python3 javac java sha256sum mktemp mv; do
    command -v "$dep" >/dev/null 2>&1 || { echo "Preflight missing: $dep" >&2; exit 12; }
done
[[ -f "$HOME/Downloads/$ARCHIVE" ]] || { echo 'Source archive missing on NAS' >&2; exit 13; }
printf '%s  %s\n' "$EXPECTED" "$HOME/Downloads/$ARCHIVE" | sha256sum -c - || { echo 'NAS SHA-256 mismatch' >&2; exit 29; }
unzip -tq "$HOME/Downloads/$ARCHIVE" >/dev/null || { echo 'Source ZIP integrity failed' >&2; exit 29; }
STAGE="$(mktemp -d "$ROOT/releases/.incoming.XXXXXXXX")"
COMMITTED=0; SUCCESS=0; POINTER_UPDATED=0
PREVIOUS_POINTER=''
if [[ -L "$ROOT/current" ]]; then PREVIOUS_POINTER="$(readlink "$ROOT/current")"; fi
rollback(){
    local result="$?"
    if (( result != 0 )); then
        if (( POINTER_UPDATED == 1 )); then
            if [[ -n "$PREVIOUS_POINTER" ]]; then
                ln -s -- "$PREVIOUS_POINTER" "$ROOT/.rollback.$$"
                mv -Tf -- "$ROOT/.rollback.$$" "$ROOT/current" || true
            else
                rm -f -- "$ROOT/current"
            fi
        fi
        echo "[NAS] BUILD FAILED ($result); prior published APK preserved" >&2
    fi
    rm -f -- "$ROOT/.current.$$" "$ROOT/.rollback.$$" 2>/dev/null || true
    if (( COMMITTED == 0 )) && [[ -n "${STAGE:-}" && -d "$STAGE" ]]; then
        rm -rf -- "$STAGE"
    fi
}
trap rollback EXIT
unzip -oq "$HOME/Downloads/$ARCHIVE" -d "$STAGE"
[[ -f "$STAGE/$SRC_DIR/BUILD_ON_NAS.sh" ]] || { echo 'Source directory incomplete' >&2; exit 14; }
mkdir -p -- "$STAGE/apk" "$STAGE/tmp"
# Host gate scratch must not compete with RAM-backed /tmp on low-memory NAS systems.
# Scope the directory to the isolated transaction; rollback removes it on failure.
export TMPDIR="$STAGE/tmp"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.io.tmpdir=\"$TMPDIR\""
printf '[NAS] disk-backed scratch: %s\n' "$TMPDIR"
cd "$STAGE/$SRC_DIR"
VINYL_APK_OUTPUT="$STAGE/apk/$APK" bash BUILD_ON_NAS.sh
[[ -s "$STAGE/apk/$APK" ]] || { echo 'Built APK missing from isolated stage' >&2; exit 32; }
rm -rf -- "$STAGE/tmp"
RELEASE="$ROOT/releases/$(date +%Y%m%d-%H%M%S)-$$.release"
mv -- "$STAGE" "$RELEASE"
COMMITTED=1
# Publish source pointer first; if APK copy fails the exit trap reverts it.
ln -s -- "$RELEASE/$SRC_DIR" "$ROOT/.current.$$"
mv -Tf -- "$ROOT/.current.$$" "$ROOT/current"
POINTER_UPDATED=1
TMP_APK="$(mktemp "$HOME/Downloads/.VinylStudio-V3-release.XXXXXXXX")"
trap 'rollback; rm -f -- "${TMP_APK:-}"' EXIT
cp -- "$RELEASE/apk/$APK" "$TMP_APK"
[[ "$(sha256sum "$TMP_APK" | awk '{print $1}')" == "$(sha256sum "$RELEASE/apk/$APK" | awk '{print $1}')" ]] || { echo 'APK publication checksum mismatch' >&2; exit 29; }
mv -f -- "$TMP_APK" "$HOME/Downloads/$APK"
SUCCESS=1
printf '[NAS] SUCCESS: published source at %s and verified APK in Downloads
' "$ROOT/current"

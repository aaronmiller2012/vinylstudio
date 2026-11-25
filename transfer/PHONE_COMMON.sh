#!/data/data/com.termux/files/usr/bin/bash
# Shared phone-side verification, diagnostics, retries, and safe receive.
# Sourced by both RC125 scripts. Does not persist secrets or install packages.
set -Eeuo pipefail
readonly VINYL_RETRIES=3
NAS_HOST="${NAS_HOST:-user@nas.example}"
DEST="${PHONE_DOWNLOADS:-$HOME/storage/downloads}"
VINYL_LOG_DIR="${VINYL_LOG_DIR:-$HOME/.local/state/vinylstudio/logs}"
VINYL_TMP_APK=''
VINYL_SOCKDIR=''

vinyl_fail(){ local code="$1"; shift; printf 'ERROR [E%s]: %s\n' "$code" "$*" >&2; exit "$code"; }
vinyl_notice(){
    local text="$1"
    printf '\a%s\n' "$text" >&2
    if command -v termux-notification >/dev/null 2>&1; then
        termux-notification --id 114 --title 'VinylStudio transfer' --content "$text" >/dev/null 2>&1 || true
    fi
}
vinyl_exit(){
    local result="$1"
    trap - EXIT
    [[ -z "$VINYL_TMP_APK" ]] || rm -f -- "$VINYL_TMP_APK"
    if [[ -n "$VINYL_SOCKDIR" ]]; then
        ssh -S "$VINYL_SOCKDIR/control" -O exit "$NAS_HOST" >/dev/null 2>&1 || true
        rm -rf -- "$VINYL_SOCKDIR"
    fi
    if (( result != 0 )); then
        vinyl_notice "Transfer stopped (exit $result). See $VINYL_LOG_FILE"
    else
        printf 'Transfer complete. Log: %s\n' "$VINYL_LOG_FILE"
    fi
}
vinyl_init(){
    local mode="$1"
    umask 077
    for c in ssh scp sha256sum awk mktemp tee date mv mkdir rm; do
        command -v "$c" >/dev/null 2>&1 || vinyl_fail 12 "Missing $c. Install openssh/coreutils in Termux."
    done
    [[ -d "${HOME:-/nonexistent}" ]] || vinyl_fail 13 'Termux home is missing'
    [[ -d "$DEST" ]] || mkdir -p -- "$DEST" || vinyl_fail 13 "Cannot create output directory: $DEST (run termux-setup-storage if necessary)"
    [[ -w "$DEST" ]] || vinyl_fail 13 "Output directory not writable: $DEST"
    mkdir -p -- "$VINYL_LOG_DIR" "$HOME/.ssh" || vinyl_fail 13 'Cannot create private log/SSH directory'
    chmod 700 "$VINYL_LOG_DIR" "$HOME/.ssh" || vinyl_fail 13 'Cannot secure private log/SSH directory'
    VINYL_LOG_FILE="$VINYL_LOG_DIR/${mode}-$(date +%Y%m%d-%H%M%S)-$$.log"
    : > "$VINYL_LOG_FILE"
    exec > >(tee -a "$VINYL_LOG_FILE") 2>&1
    trap 'vinyl_exit "$?"' EXIT
    VINYL_SOCKDIR="$(mktemp -d "$HOME/.ssh/vinyl-XXXXXXXX")" || vinyl_fail 13 'Cannot create SSH socket directory'
    VINYL_SSH_OPTS=(-o ConnectTimeout=20 -o ServerAliveInterval=20 -o ServerAliveCountMax=3 \
       -o ControlMaster=auto -o ControlPersist=300 -o "ControlPath=$VINYL_SOCKDIR/control")
    printf '[%s] Begin %s to %s\n' "$(date -Is)" "$mode" "$NAS_HOST"
}
vinyl_connect(){
    printf 'Open one reusable SSH session. Enter the NAS password once if prompted.\n'
    ssh "${VINYL_SSH_OPTS[@]}" -o ControlMaster=yes -Nf "$NAS_HOST" ||
        vinyl_fail 20 'NAS authentication failed (check password, SSH address, or Tailscale)'
    ssh -S "$VINYL_SOCKDIR/control" -O check "$NAS_HOST" >/dev/null 2>&1 ||
        vinyl_fail 20 'Reusable SSH session did not start'
}
vinyl_ssh(){ ssh "${VINYL_SSH_OPTS[@]}" "$NAS_HOST" "$@"; }
vinyl_remote_hash(){
    local filename="$1" sha
    # Filenames are embedded release constants, not user-supplied remote commands.
    sha="$(vinyl_ssh bash -s -- "$filename" <<'REMOTE'
set -euo pipefail
file="$HOME/Downloads/$1"
[[ -s "$file" ]] || exit 2
sha256sum "$file" | awk '{print $1}'
REMOTE
)" || return 1
    [[ "$sha" =~ ^[0-9a-f]{64}$ ]] || return 1
    printf '%s' "$sha"
}
vinyl_send_source(){
    local base="$1" archive="$2" expected="$3"
    [[ -s "$base/$archive" ]] || vinyl_fail 14 "Source archive missing: $base/$archive"
    [[ -s "$base/SOURCE_SHA256.txt" ]] || vinyl_fail 14 'SOURCE_SHA256.txt missing'
    (cd "$base" && sha256sum -c SOURCE_SHA256.txt) || vinyl_fail 14 'Local source checksum mismatch; download the TransferKit again'
    [[ "$(sha256sum "$base/$archive" | awk '{print $1}')" == "$expected" ]] || vinyl_fail 14 'Manifest SHA mismatch'
    if [[ "$(vinyl_remote_hash "$archive" 2>/dev/null || true)" == "$expected" ]]; then
        vinyl_ssh bash -s -- "$archive" "$expected" <<'REMOTE' || vinyl_fail 29 'Could not update cached source manifest'
set -euo pipefail
cd "$HOME/Downloads"
printf '%s  %s\n' "$2" "$1" > "$1.sha256"
sha256sum -c "$1.sha256"
REMOTE
        printf 'SOURCE CACHE HIT: remote SHA-256 matches; upload skipped.\n'
        return 0
    fi
    local attempt temp=".${archive}.upload.$$" actual=ok
    for attempt in 1 2 3; do
        printf '[source attempt %d/%d] Uploading with verified staging\n' "$attempt" "$VINYL_RETRIES"
        actual=bad
        # rsync uses delta transfer when installed on both ends; scp is safe fallback.
        if command -v rsync >/dev/null 2>&1 && vinyl_ssh 'command -v rsync >/dev/null' 2>/dev/null; then
            local rsync_rsh="ssh -o ControlMaster=no -o ControlPath=$VINYL_SOCKDIR/control -o ConnectTimeout=20 -o ServerAliveInterval=20 -o ServerAliveCountMax=3"
            if rsync -a --delay-updates --partial-dir=.vinyl-rsync-partial \
                -e "$rsync_rsh" \
                "$base/$archive" "$NAS_HOST:Downloads/$temp"; then
                actual=ok
            else
                printf 'rsync unavailable/failed; falling back to scp.\n' >&2
                if scp "${VINYL_SSH_OPTS[@]}" "$base/$archive" "$NAS_HOST:Downloads/$temp"; then actual=ok; fi
            fi
        else
            if scp "${VINYL_SSH_OPTS[@]}" "$base/$archive" "$NAS_HOST:Downloads/$temp"; then actual=ok; fi
        fi
        if [[ "$actual" == ok ]]; then
            if vinyl_ssh bash -s -- "$temp" "$archive" "$expected" <<'REMOTE'
set -euo pipefail
cd "$HOME/Downloads"
[[ "$(sha256sum "$1" | awk '{print $1}')" == "$3" ]] || exit 29
mv -f -- "$1" "$2"
printf '%s  %s\n' "$3" "$2" > "$2.sha256"
sha256sum -c "$2.sha256"
REMOTE
            then
                if [[ "$(vinyl_remote_hash "$archive" 2>/dev/null || true)" == "$expected" ]]; then
                    printf 'SOURCE VERIFIED on NAS: %s\n' "$expected"
                    return 0
                fi
            fi
        fi
        printf 'Transfer or checksum failure; destination unchanged, retrying.\n' >&2
        vinyl_ssh bash -s -- "$temp" <<'REMOTE' || true
rm -f -- "$HOME/Downloads/$1"
REMOTE
        if (( attempt < VINYL_RETRIES )); then sleep "$((attempt*2))"; fi
    done
    vinyl_fail 29 'Source transfer failed SHA-256 verification after 3 attempts'
}
vinyl_receive_apk(){
    local apk="$1" expected attempt observed
    expected="$(vinyl_remote_hash "$apk")" || vinyl_fail 30 "Remote APK missing: $apk; check NAS build log"
    VINYL_TMP_APK="$DEST/.${apk}.$$.$RANDOM.part"
    for attempt in 1 2 3; do
        printf '[APK attempt %d/%d] Receiving %s\n' "$attempt" "$VINYL_RETRIES" "$apk"
        if scp "${VINYL_SSH_OPTS[@]}" "$NAS_HOST:Downloads/$apk" "$VINYL_TMP_APK"; then
            observed="$(sha256sum "$VINYL_TMP_APK" | awk '{print $1}')"
            if [[ "$observed" == "$expected" ]]; then
                mv -f -- "$VINYL_TMP_APK" "$DEST/$apk" || vinyl_fail 31 'Cannot publish verified APK'
                VINYL_TMP_APK=''
                printf 'APK VERIFIED: %s (%s)\n' "$DEST/$apk" "$expected"
                return 0
            fi
        fi
        rm -f -- "$VINYL_TMP_APK"
        printf 'APK transfer/checksum mismatch; previous APK remains intact.\n' >&2
        if (( attempt < VINYL_RETRIES )); then sleep "$((attempt*2))"; fi
    done
    vinyl_fail 32 'APK failed checksum verification after 3 attempts; original APK preserved'
}

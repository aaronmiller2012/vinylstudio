#!/usr/bin/env bash
# Builds a signed Play upload bundle using a separately provisioned upload key.
# Does not change the debug APK workflow, install an application, or upload to Play.
set -Eeuo pipefail
umask 077
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
fail(){ printf 'PLAY BUILD ERROR: %s\n' "$*" >&2; exit 1; }
for dep in bash python3 javac java keytool jarsigner sha256sum unzip mktemp mv awk curl; do
  command -v "$dep" >/dev/null 2>&1 || fail "Missing $dep on NAS"
done
for key in VINYL_RELEASE_KEYSTORE VINYL_RELEASE_ALIAS VINYL_RELEASE_STORE_PASSWORD VINYL_RELEASE_KEY_PASSWORD VINYL_PRIVACY_URL VINYL_PRIVACY_CONTACT; do
  [[ -n "${!key:-}" ]] || fail "Set $key privately on NAS; no unsigned AAB will be published"
done
[[ -f "$VINYL_RELEASE_KEYSTORE" && -r "$VINYL_RELEASE_KEYSTORE" ]] || fail 'Keystore does not exist or is unreadable'
[[ "$VINYL_RELEASE_KEYSTORE" = /* ]] || fail 'Use an absolute keystore path outside the project or TransferKit'
case "$VINYL_RELEASE_KEYSTORE" in "$PWD"/*) fail 'Store the upload key outside the source tree';; esac
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
[[ -f "$SDK/platforms/android-36/android.jar" ]] || fail 'Android SDK platform 36 not found; configure ANDROID_HOME'
export ANDROID_HOME="$SDK"
# Authenticate alias and keystore password before time-consuming compilation.
# Password is taken from env, never printed or interpolated into a command-line arg.
keytool -list -keystore "$VINYL_RELEASE_KEYSTORE" -alias "$VINYL_RELEASE_ALIAS" \
  -storepass:env VINYL_RELEASE_STORE_PASSWORD >/dev/null || fail 'Keystore, alias, or store password invalid'
[[ -f ./gradlew ]] || fail 'Gradle wrapper missing'
[[ -x ./gradlew ]] || chmod u+x ./gradlew || fail 'Cannot execute Gradle wrapper'
[[ -f gradle/wrapper/gradle-wrapper.properties ]] || fail 'Gradle wrapper properties missing'
printf '\n===== LIVE PRIVACY POLICY PRE-FLIGHT =====\n'
bash scripts/check-public-privacy-policy.sh
python3 tests/check-rc173-wrapper-bootstrap.py
printf '\n===== VERIFIED GRADLE WRAPPER =====\n'
bash scripts/ensure-gradle-wrapper.sh
printf '\n===== LATEST HOST TESTS AND PLAY POLICY CHECKS =====\n'
python3 tests/check-rc115-play.py
# The Play bundle must pass the current development release's new geometry gate,
# not only the inherited Beta4/RC173 baseline checks.
bash tests/run-v3-product555-gate.sh
bash tests/run-v3-product554-gate.sh
bash tests/run-v3-product553-gate.sh
bash tests/run-v3-precision552-gate.sh
bash tests/run-v3-reliability551-gate.sh
bash tests/run-v3-stability550-gate.sh
bash tests/run-v3-workshop549-gate.sh
bash tests/run-v3-milestone-ab-completion-gate.sh
bash tests/run-v3-organic-smoothing-gate.sh
bash tests/run-v3-render-speed-gate.sh
bash tests/run-v3-math-repair-gate.sh
bash tests/run-v3-overlay-swatch-gate.sh
bash tests/run-v3-consensus-color-speed-gate.sh
bash tests/run-v3-slider-overprint-repair-gate.sh
bash tests/run-v3-prismatic-weave-gate.sh
bash tests/run-v3-smart-palette-gate.sh
bash tests/run-v3-render-pipeline-gate.sh
bash tests/run-v3-performance-architecture-gate.sh
bash tests/run-v3-material-calibration-gate.sh
bash tests/run-v3-user-guide-gate.sh
bash tests/run-v3-portrait-detail-restore-gate.sh
bash tests/run-v3-raster-motif-polish-gate.sh
bash tests/run-v3-vector-boolean-gate.sh
bash tests/run-v3-perceptual-swatch-gate.sh
bash tests/run-v3-overprint-cells-gate.sh
bash tests/run-v3-style-retirement-gate.sh
bash tests/run-v3-beta4-host-gate.sh
bash tests/run-v3-milestone-a1-gate.sh
bash tests/run-v3-milestone-a2-gate.sh
bash tests/run-v3-milestone-a3-b1-gate.sh
# >>> synced from tests/gates.manifest by scripts/sync-gates.py (do not edit by hand)
bash tests/run-v3-m6-completion-gate.sh
bash tests/run-v3-m6-refinement-gate.sh
bash tests/run-v3-m6-confetti-gate.sh
bash tests/run-v3-m6-reliability-gate.sh
bash tests/run-v3-m5-release-gate.sh
bash tests/run-v3-prismatic-ribbon-gate.sh
bash tests/run-v3-m5-export-mat-gate.sh
bash tests/run-v3-m5-lifecycle-gate.sh
bash tests/run-v3-m5-color-gate.sh
bash tests/run-v3-m5-prismatic-perf-gate.sh
bash tests/run-v3-m5-crochet-smoothing-gate.sh
bash tests/run-v3-m5-lines-gate.sh
bash tests/run-v3-m5-linocut-gate.sh
bash tests/run-v3-m4-quality-gate.sh
bash tests/run-v3-m3-editor-state-gate.sh
bash tests/run-v3-m3-library-reliability-gate.sh
bash tests/run-v3-product556-gate.sh
bash tests/run-v3-export-sheet-contract-gate.sh
bash tests/run-v3-m2-render-gate.sh
bash tests/run-v3-slider-guide-gate.sh
bash tests/run-v3-selective-ridge-polish-gate.sh
bash tests/run-v3-style-selection-gate.sh
bash tests/run-v3-organic-render-recovery-gate.sh
bash tests/run-v3-highgain-optimization-gate.sh
bash tests/run-v3-fix7-hardening-gate.sh
bash tests/run-v3-fix8-cut-tune-gate.sh
bash tests/run-v3-fix8-audit-repair-gate.sh
bash tests/run-v3-color-pipeline-repair-gate.sh
bash tests/run-v3-fix14-tonal-palette-gate.sh
bash tests/run-v3-fix15-tonal-order-gate.sh
bash tests/run-v3-fix10-geometry-options-gate.sh
bash tests/run-v3-snip-detail-gate.sh
bash tests/run-v3-fix12-crop-parity-gate.sh
bash tests/run-v3-fix13-postsnip-vectors-gate.sh
bash tests/run-v3-research-defect-gate.sh
bash tests/run-v3-beta26-export-matrix-gate.sh
bash tests/run-v3-organic-flow-gate.sh
bash tests/run-v3-organic-feature-preservation-gate.sh
bash tests/run-v3-organic-nochange-gate.sh
bash tests/run-v3-organic-medium-contours-gate.sh
bash tests/run-v3-organic-render-optimization-gate.sh
bash tests/run-v3-pop-poster-gate.sh
bash tests/run-v3-beta10-tonal-all-gate.sh
bash tests/run-v3-milestone-b2-gate.sh
bash tests/run-v3-milestone-b2-lines-gate.sh
bash tests/run-v3-milestone-ab-wrap-c1-gate.sh
bash tests/run-v3-milestone-c2-gate.sh
bash tests/run-v3-milestone-c3-gate.sh
bash tests/run-v3-shapes-area-tone-gate.sh
bash tests/run-v3-dense-export-gate.sh
bash tests/run-v3-star-cut-safe-gate.sh
bash tests/run-v3-prepared-export-gate.sh
bash tests/run-v3-beta25-snip-mat-gate.sh
bash tests/run-v3-release-hardening-gate.sh
bash tests/run-v3-registration-gate.sh
bash tests/run-v3-beta9-tonal-gate.sh
bash tests/run-v3-host-gate.sh
bash tests/run-v3-golden-gate.sh
# <<< end synced gates
bash tests/run-rc125-palette-offset-gate.sh
printf '\n===== TEST, LINT, R8 AND SIGN RELEASE =====\n'
# Do not publish an AAB if any required test, lint or release compilation fails.
./gradlew --no-daemon --max-workers=1 --console=plain --build-cache \
  :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease
MAP='app/build/outputs/mapping/release/mapping.txt'
[[ -s "$MAP" ]] || fail 'R8 mapping file missing; release shrinking has not been verified'
[[ -s 'app/build/outputs/apk/release/app-release.apk' ]] || fail 'Signed release APK missing; cannot test the release variant on-device' 
BUNDLE='app/build/outputs/bundle/release/app-release.aab'
[[ -s "$BUNDLE" ]] || fail 'Release bundle missing or empty'
# Java/JAR signature is correct for AAB (apksigner is for APK, not AAB).
# The grep disallows treating an unsigned bundle or an error as a valid artifact.
VERIFY="$(jarsigner -verify -verbose "$BUNDLE" 2>&1)" || fail 'Bundle signature verification failed'
grep -Fq 'jar verified.' <<< "$VERIFY" || fail 'No valid JAR signature reported'
BUNDLE_ENTRIES="$(unzip -Z1 "$BUNDLE")" || fail 'Cannot list AAB contents'
grep -Eq '^META-INF/[^/]+\.(RSA|DSA|EC)$' <<< "$BUNDLE_ENTRIES" || fail 'AAB has no signing certificate entry'
# Optional bundletool validation, when available on the NAS.
if [[ -n "${BUNDLETOOL_JAR:-}" ]]; then
  [[ -f "$BUNDLETOOL_JAR" ]] || fail 'BUNDLETOOL_JAR set but missing'
  java -jar "$BUNDLETOOL_JAR" validate --bundle="$BUNDLE" || fail 'bundletool validation failed'
else
  echo 'NOTE: bundletool validation was not run; set BUNDLETOOL_JAR to enable it.'
fi
# Reject unexpected native libraries until their 16 KB alignment has been checked.
if grep -Eq '^base/lib/[^/]+/[^/]+\.so$' <<< "$BUNDLE_ENTRIES"; then
  [[ -n "${BUNDLETOOL_JAR:-}" ]] || fail 'Native libraries present; run bundletool and verify 16 KB page compatibility before publishing'
  echo 'WARNING: Bundle contains native libraries; 16 KB ELF segment alignment still needs independent verification.' >&2
fi
DEST="${VINYL_PLAY_AAB_OUTPUT:-$HOME/Downloads/VinylStudio-v3.5.4-Play565.aab}"
MAP_DEST="${DEST%.aab}-mapping.txt"
mkdir -p -- "$(dirname -- "$DEST")"
STAGED="$(mktemp "$(dirname -- "$DEST")/.vinyl-play-aab.XXXXXXXX")"
MAP_STAGE="$(mktemp "$(dirname -- "$MAP_DEST")/.vinyl-mapping.XXXXXXXX")"
trap 'rm -f -- "$STAGED" "$MAP_STAGE"' EXIT
cp -- "$BUNDLE" "$STAGED"
cp -- "$MAP" "$MAP_STAGE"
[[ "$(sha256sum "$BUNDLE" | awk '{print $1}')" == "$(sha256sum "$STAGED" | awk '{print $1}')" ]] || fail 'AAB checksum mismatch during staging'
[[ "$(sha256sum "$MAP" | awk '{print $1}')" == "$(sha256sum "$MAP_STAGE" | awk '{print $1}')" ]] || fail 'Mapping checksum mismatch during staging'
# Publishing the mapping FIRST avoids a visible new AAB without its R8 symbols.
# No cross-file atomic rename exists; an orphan mapping is recoverable, missing mapping is not.
mv -f -- "$MAP_STAGE" "$MAP_DEST"
[[ -s "$MAP_DEST" ]] || fail 'Published mapping missing; bundle NOT published'
mv -f -- "$STAGED" "$DEST"
trap - EXIT
printf '\nSIGNED RELEASE AAB VERIFIED: %s\n' "$DEST"
sha256sum "$DEST" "$MAP_DEST"
echo 'The corresponding release APK is for manual on-device testing; back up projects before installing when switching signing certificates.'
echo 'Upload manually through Play Console after completing privacy, Data safety, rating, and listing requirements.'

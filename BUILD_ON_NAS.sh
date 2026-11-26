#!/usr/bin/env bash
set -Eeuo pipefail
# Report the command and actual status even when an inherited gate fails silently.
trap 'rc=$?; printf "[NAS] command failed (exit %s) at line %s: %s\n" "$rc" "$LINENO" "$BASH_COMMAND" >&2; exit "$rc"' ERR
cd "$(dirname "$0")"

printf '\n===== MANDATORY V3 OPAQUE OVERLAY PALETTE + INHERITED QA GATES =====\n'
for dep in bash python3 javac java unzip sha256sum mktemp curl; do
  command -v "$dep" >/dev/null 2>&1 || { echo "Missing build prerequisite: $dep" >&2; exit 12; }
done
case "${VINYL_QUALIFY_RELEASE:-0}" in 0|1) ;; *) echo 'VINYL_QUALIFY_RELEASE must be 0 or 1.' >&2; exit 12;; esac
[[ -d "${ANDROID_HOME:-$HOME/Android/Sdk}" ]] || {
  echo 'Android SDK directory missing; set ANDROID_HOME before starting tests or transfer' >&2;exit 3;
}
bash tests/run-nas-source-preflight.sh
# Compile Android code early so UI mistakes fail before CPU-heavy golden tests.
# SDK installation and final APK compilation remain below for fresh NAS setup.
EARLY_SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [[ -f "$EARLY_SDK/platforms/android-36/android.jar" ]]; then
  export ANDROID_HOME="$EARLY_SDK"
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
  [[ -x ./gradlew ]] || chmod u+x ./gradlew
  printf '\n===== EARLY ANDROID JAVA COMPILE =====\n'
  bash scripts/ensure-gradle-wrapper.sh
  ./gradlew --no-daemon --max-workers=1 --console=plain --build-cache :app:compileDebugJavaWithJavac
fi
bash tests/run-v3-product555-gate.sh
bash tests/run-v3-product554-gate.sh
bash tests/run-v3-product553-gate.sh
bash tests/run-v3-precision552-gate.sh
bash tests/run-v3-reliability551-gate.sh
bash tests/run-v3-stability550-gate.sh
bash tests/run-v3-workshop549-gate.sh
bash tests/run-v3-milestone-ab-completion-gate.sh
bash tests/run-v3-organic-smoothing-gate.sh
bash tests/run-v3-math-repair-gate.sh
bash tests/run-v3-render-speed-gate.sh
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
bash tests/run-v3-selective-ridge-polish-gate.sh
bash tests/run-v3-overprint-cells-gate.sh
bash tests/run-v3-style-retirement-gate.sh
bash tests/run-v3-style-selection-gate.sh
bash tests/run-v3-organic-render-recovery-gate.sh
# Exact percentile, cached photo/Snip invalidation, changed-contour only,
# primitive spatial-grid parity. This is a mandatory correctness gate.
bash tests/run-v3-highgain-optimization-gate.sh
python3 tests/check-current-release.py
python3 tests/test-v3-release-archive-contract.py
python3 tests/check-v3-release-consistency.py
bash tests/run-v3-fix7-hardening-gate.sh
printf '\n===== FIX8 CUT TUNING: VINYL/WASTE INSPECTION AND STYLE GEOMETRY =====\n'
bash tests/run-v3-fix8-cut-tune-gate.sh
bash tests/run-v3-fix8-audit-repair-gate.sh
# Transactional project open and color-state regressions (host + Android source gates).
bash tests/run-v3-color-pipeline-repair-gate.sh
# Tonal source-ID / physical-stack swatches and Styles navigation regressions.
bash tests/run-v3-fix14-tonal-palette-gate.sh
# Default toner presets follow photographic brightness, not physical L1 ordering.
bash tests/run-v3-fix15-tonal-order-gate.sh
# Validate actual default and custom material gaps before expensive inherited suites.
bash tests/run-v3-fix10-geometry-options-gate.sh
# Snipped and unsnipped photo interiors must retain the same CMYK line tone.
bash tests/run-v3-snip-detail-gate.sh
bash tests/run-v3-fix12-crop-parity-gate.sh
# Stop any pre-tone Snip dependency from reaching shipping line geometry.
bash tests/run-v3-fix13-postsnip-vectors-gate.sh
python3 tests/check-version-transition.py
python3 tests/check-rc113-rollback.py
printf '\n===== RESEARCH DEFECT REGRESSION: CUT SAFETY, ORIGINALS, QUEUES, DXF =====\n'
bash tests/run-v3-research-defect-gate.sh
# Stable renderer IDs, retired-style tombstones, New Project, multi-page
# guide, source syntax, and the complete inherited RC140 geometry/quality gate.
bash tests/run-v3-beta26-export-matrix-gate.sh
bash tests/run-v3-organic-flow-gate.sh
# Adversarial checks: eye/lip islands, eye cutouts, 0% vs 100% portrait fidelity.
bash tests/run-v3-organic-feature-preservation-gate.sh
# Regression: high-detail slider must round surviving paths, not only remove specks.
bash tests/run-v3-organic-nochange-gate.sh
# Cover medium-size cutouts excluded by the historic top-18-only pass.
bash tests/run-v3-organic-medium-contours-gate.sh
# Fixed 50% UI, cached quill geometry, morphology and ROI fidelity parity.
bash tests/run-v3-organic-render-optimization-gate.sh
bash tests/run-v3-pop-poster-gate.sh
bash tests/run-v3-beta10-tonal-all-gate.sh
bash tests/run-v3-beta4-host-gate.sh
bash tests/run-v3-milestone-a1-gate.sh
bash tests/run-v3-milestone-a2-gate.sh
bash tests/run-v3-milestone-a3-b1-gate.sh
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
bash tests/run-v3-registration-gate.sh
bash tests/run-v3-beta9-tonal-gate.sh
bash tests/run-v3-host-gate.sh
bash tests/run-v3-golden-gate.sh
# <<< end synced gates

# Inherited RC119 tests are available through FULL_TESTS=1 because
# the recursive old suite is lengthy on the low-memory NAS.
# Legacy full gate is optional because the inherited photometric regression can
# take much longer on a low-memory NAS. It remains available for qualification.

# Run the long historical suite only when explicitly requested; a full suite can
# take minutes on the low-memory Debian NAS. The extended historical suites were not run end-to-end in the
# source audit environment; enable it to run on your NAS when convenient.
if [[ "${FULL_TESTS:-0}" == "1" ]]; then
  printf '\n===== FULL HISTORICAL SOURCE CHECKS =====\n'
  bash tests/run-rc119-fish-scale-gate.sh
  bash tests/run-v25471-banknote-gate.sh
  bash tests/run-v2526-gate.sh
  bash tests/run-release-smoke.sh
  bash tests/run-layered-release-checks.sh
  bash tests/run-host-tests.sh
fi

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [[ ! -d "$SDK" ]]; then
  echo "Android SDK not found: $SDK. Set ANDROID_HOME to your existing SDK." >&2
  exit 3
fi
export ANDROID_HOME="$SDK"
if [[ ! -f "$SDK/platforms/android-36/android.jar" ]]; then
  SDKMANAGER="$(find "$SDK/cmdline-tools" -maxdepth 3 -type f -name sdkmanager -print -quit 2>/dev/null || true)"
  if [[ -z "$SDKMANAGER" ]]; then echo "Install Android SDK platform 36 with your Android command line tools." >&2;exit 5;fi
  echo 'Installing Android API 36 into the existing Android SDK (required for Google Play target 36).'
  "$SDKMANAGER" --sdk_root="$SDK" 'platforms;android-36'
fi
printf 'sdk.dir=%s\n' "$SDK" > local.properties

printf '\n===== VERIFY GRADLE WRAPPER =====\n'
bash scripts/ensure-gradle-wrapper.sh
[[ -f ./gradlew ]] || { echo 'Missing project Gradle wrapper' >&2;exit 4; }
# Some Android unzip/transfer apps strip UNIX executable bits from ZIP entries.
# This changes only the extracted project script, never host networking/services.
if [[ ! -x ./gradlew ]]; then chmod u+x ./gradlew || { echo 'Cannot make Gradle wrapper executable' >&2;exit 4; }; fi
if ! WRAPPER_INFO=$(./gradlew --version); then
  echo 'Gradle wrapper bootstrap failed. Review the download or integrity error above.' >&2
  exit 4
fi
printf '%s\n' "$WRAPPER_INFO"
grep -q 'Gradle 8.11.1' <<< "$WRAPPER_INFO" || { echo 'Gradle 8.11.1 required' >&2; exit 4; }
printf '\n===== ANDROID DEBUG BUILD + REAL DEBUG JUNIT + LINT =====\n'
GRADLE_FLAGS=(--no-daemon --max-workers=1 --console=plain --build-cache)
# Configuration caching is opt-in because custom Android build logic may reject it.
if [[ "${VINYL_CONFIG_CACHE:-0}" == "1" ]]; then GRADLE_FLAGS+=(--configuration-cache); fi
if ! ./gradlew "${GRADLE_FLAGS[@]}" :app:testDebugUnitTest :app:lintDebug :app:assembleDebug; then
  echo 'Development Gradle tests/lint/assemble failed; APK will NOT be published.' >&2
  REPORT="app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt"
  if [[ -f "$REPORT" ]]; then
    printf '\n===== COMPLETE ANDROID LINT ERRORS =====\n' >&2
    grep -E ':[0-9]+: Error:|^Lint found [0-9]+ errors' "$REPORT" >&2 || true
    printf 'Full lint report: %s\n' "$(pwd)/$REPORT" >&2
  fi
  exit 40
fi
# Development APK builds must not require a publicly hosted privacy policy.
# Release tasks stay fail-closed through app/build.gradle validateReleasePrivacy.
case "${VINYL_QUALIFY_RELEASE:-0}" in
  0) printf '\n===== DEVELOPMENT BUILD: release qualification deferred =====\n'
     printf 'No privacy policy URL is required for the DEBUG APK.\n'
     printf 'Release JUnit, release lint, R8 and bundle are NOT certified by this run.\n'
     ;;
  1)
     [[ -n "${VINYL_PRIVACY_URL:-}" ]] || {
       echo 'Release qualification requested but VINYL_PRIVACY_URL is missing.' >&2
       echo 'Host your actual HTTPS privacy policy before enabling this mode.' >&2
       exit 41
     }
     printf '\n===== RELEASE QUALIFICATION: JUNIT / RELEASE LINT / R8 =====\n'
     ./gradlew "${GRADLE_FLAGS[@]}" :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
     [[ -s app/build/outputs/mapping/release/mapping.txt ]] || {
       echo 'R8 shrinker mapping missing; release shrink verification incomplete' >&2;exit 40;
     }
     # Map must be retained with the exact R8 release build that produced it.
     mkdir -p release-artifacts
     cp -- app/build/outputs/mapping/release/mapping.txt release-artifacts/V3-code565-mapping.txt
     sha256sum release-artifacts/V3-code565-mapping.txt > release-artifacts/V3-code565-mapping.sha256
     printf 'RELEASE QUALIFICATION COMPILED; actual device checks remain required.\n'
     ;;
  *) echo 'Invalid VINYL_QUALIFY_RELEASE. Set 0 (development) or 1 (release qualification).' >&2
     exit 12;;
esac
APK=app/build/outputs/apk/debug/app-debug.apk
[[ -s "$APK" ]] || { echo "APK build output missing" >&2; exit 5; }
DEST="${VINYL_APK_OUTPUT:-$HOME/Downloads/VinylHalftoneStudio-v3.5.4-Product565.apk}"
# Do not clobber the previous working artifact on a short or interrupted copy.
mkdir -p -- "$(dirname "$DEST")"
STAGED_APK="$(mktemp "$(dirname "$DEST")/.VinylStudio-V3-apk.XXXXXXXX")"
trap 'rm -f -- "${STAGED_APK:-}"' EXIT
cp "$APK" "$STAGED_APK"
[[ "$(sha256sum "$APK" | awk '{print $1}')" == "$(sha256sum "$STAGED_APK" | awk '{print $1}')" ]] || {
  echo 'APK failed local post-copy checksum' >&2;exit 29;
}
mv -f -- "$STAGED_APK" "$DEST"
trap - EXIT
printf '\n===== BUILT =====\n'
sha256sum "$DEST"
ls -lh "$DEST"
echo 'DEVELOPMENT APK ONLY. Not Play-approved; no release qualification unless VINYL_QUALIFY_RELEASE=1.'
echo "Previous installed application data is preserved only if you sign with the same existing Android debug key."

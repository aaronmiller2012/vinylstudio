# Quality gates

## Levels of evidence (always state which one a claim rests on)

1. **Compiles:** `:app:compileDebugJavaWithJavac`.
2. **Automated:** `tests/gates.manifest` (all gates) + `testDebugUnitTest` + `lintDebug`.
3. **Release-variant:** `VINYL_QUALIFY_RELEASE=1` (release JUnit, lint, R8 mapping). **Not yet run for 555/556.**
4. **Device:** release-variant APK installed on the S22 Ultra, `scripts/run-device-tests.sh` (StageOneInstrumentation, 11 cases) + `QA_DEVICE_CHECKLIST.md`.
   - Note: `BUILD_ON_NAS.sh:206` always delivers the **debug** APK.
5. **Cutter-import:** Design Space / Silhouette import checks, recorded in `qualification/`.
6. **Physical cut:** recorded in `qualification/`.

## What exists

| Kind | Count | Behavioral? |
|---|---|---|
| `tests/check-*.py` | 239 | About 231 are source-text assertions (substring/regex on `.java`). They protect structure and wording, not behavior |
| `tests/*Test.java` (host JVM against `tests/android-host-stubs`) | about 318 | Yes: golden signatures, export matrix, geometry, library, session state |
| `tests/run-v3-*-gate.sh` | 70 in the manifest | Orchestrators. Several compile hand-listed sources, which must be updated when dependencies change |
| `app/src/test` (Gradle JUnit) | 7 | Yes |
| `app/src/androidTest` | 1 file, 11 cases | Device |

## Golden tests

- `tests/V3StyleGoldenTest.java` compares closed/circle/open contour counts and total area within max(0.5 mm², 0.5%) per style and size (`tests/golden/geometry-signatures.tsv`). The exact SVG sha256 is informational only.
- Render time is compared against `tests/golden/style-render-budget.tsv` (host wall-clock, 1 retry).
- Re-record with `VINYL_UPDATE_GOLDEN=1 bash tests/run-v3-golden-gate.sh`.
  - **Rule:** every re-record must state in the commit body which styles changed and why.
  - Never re-record to make a failing change pass without that explanation.

## Behavioral gates added on branch export-sheet-contract

| Gate | Tests | Proves |
|---|---|---|
| `run-v3-export-sheet-contract-gate.sh` | `V3ExportSheetContractTest`, `V3CricutMatRequirementTest`, `EditorAutosaveRecoveryTest` | Sheet colors, one frame, preview marks (including asymmetric custom marks), mat requirement, autosave quarantine |
| `run-v3-m2-render-gate.sh` | `V3MountingSurfaceGeometryTest`, `V3IntersectionCheckTest`, `V3PreviewExportParityTest`, `V3StatusFeedbackTest`, `check-m2-render-inputs.py` | Surface doesn't change geometry; intersection checks fail closed; cut-ready preview contours equal exported contours; status feedback and failure text |

Pitfall found while writing them: `SvgDxfR13Exporter.safetyGeometry` returns **cutter coordinates (origin bottom-left, Y up)**. Convert with `boardHeight - y` before comparing with SVG or preview coordinates. A symmetric fixture will hide a Y flip, so include asymmetric cases.

## M4 artwork-quality gate

`run-v3-m4-quality-gate.sh` runs real engines on real photographs (`tests/m4/photos`, licences in `LICENSES.md`):
palette (D12), colour schemes, D8 floors, Linocut tone mapping, gap contract, shared edges, cut-prep disclosure.
`tests/M4ArtBench.java` is the reproducible before/after benchmark (not a gate). See the M4 report for how to run it.
Metric caveats (independent review): Pearson tone correlation ignores contrast/offset (read it with dE and per-band error);
the viewing blur scales with artwork width; coverage counts anti-aliased edge pixels; even-odd per-sheet fill matches the
app preview but not overlapping same-colour pieces. Numbers support, never replace, visual review.

## M5 gates (build 561)

All are in `tests/gates.manifest` and run in CI, on the NAS and in Gradle `hostRegression`.

| Gate | What it proves (real engine output) |
|---|---|
| `run-v3-m5-release-gate.sh` | Release metadata, the manifest, and that production routes carry each adopted M5 engine change (`M5ReleaseWiringTest`; fails on the 560 source). |
| `run-v3-m5-export-mat-gate.sh` | Mat enforcement on every Cricut path incl. the single-upload SVG; compact above/below marks (identical on every layer, re-seated across raster/aspect height differences, never moving dragged marks); DXF units/entities; malformed SVG refusal. |
| `run-v3-m5-lifecycle-gate.sh` | `Settings`/`Layer`/`Config` copies are complete and independent; cancelled cut-stage jobs free their queue slot; all 38 active styles render, restore and duplicate. |
| `run-v3-m5-color-gate.sh` | Mounting surface never alters palettes; Reverse is an involution and keeps base and black; Confetti perceptual base; photo swatch subset. |
| `run-v3-m5-prismatic-perf-gate.sh` | Local micro-fragment removal equals the shared one; deterministic geometry under slow, parallel and loaded execution. |
| `run-v3-m5-crochet-smoothing-gate.sh` | Filet Crochet final geometry meets min width/gap with no lost holes at 3–11 in; Camo de-staircasing invariants (shipped off). |
| `run-v3-m5-lines-gate.sh` | Dash modulator tone conservation and run lengths; Banknote dash geometry; every line slider shown has an effect and every no-op is hidden (incl. per-layer overrides). |
| `run-v3-m5-linocut-gate.sh` | Relief polarity, seam and halo behaviour, feature marks, final-geometry cut checks at 3–11 in. |

`tests/M5Bench.java` (+ `M5Fixtures`) is the multi-scale benchmark (not a gate): CC0 photos plus deterministic synthetic portraits, 3–11 in, Snip runs, allocation and export size.

## Gaps to close (in priority order)

1. ~~Real-engine export fixtures~~: done (`V3ExportSheetContractTest`).
2. ~~Frame/mark equality across writers~~: done for standard, suite, tessellation, cell and stencil writers, the Snip base, and circle and custom marks.
3. Export geometry == cut-ready preview: done for suite styles (host). Stipple is covered by construction; device check pending.
4. Corrupt autosave preserved: done (`EditorAutosaveRecoveryTest`). Still open: restore from a backup with a corrupt middle entry; destroy during save.
5. Slider bounds vs material profile (D8).
6. Mat: the requirement is stated (`V3CricutMatRequirementTest`). The 12×12-only profile is a product decision.
7. Smoothing consistency (outer vs hole contours) per style.

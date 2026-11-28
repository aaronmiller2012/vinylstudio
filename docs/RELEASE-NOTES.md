# VinylStudio release notes (historical)

Detailed per-build notes, newest first. Some referenced internal reports are not part of the public repository.

## Build 569 (3.7.0) — M8 geometry and manufacturing candidate

Integrates the portrait maze family (builds 567–568) with the M7 device-engineering line and the M8
geometry work: deterministic cut inspection, manufacturable Confetti placement (about 95% fewer cut
findings at 8–11 in), cut-ready repair without wasted work, faster Chainmail and Confetti, artwork kept
across display changes, and an on-device R8 qualification build. See `docs/engineering/M8-569.md`.
Host-gated and measured on an Android tablet; not yet qualified in cutter software or on a physical cut.

## M7 device-engineering candidate — versionCode 566 (3.6.0)

First release measured on a real Android device (Galaxy Tab A SM-T510, non-debuggable AOT build): exact faster cut-ready conditioning and mask morphology (byte-identical output), heap-safe within-sheet parallel geometry, no re-render on workspace switches, 12 device-found UI/state defects fixed with on-device regressions. See `docs/engineering/M7-566-device.md`. Physical cut and Design Space qualification still pending.

## M6 consolidated qualification candidate — versionCode 565 (3.5.4)

Warning-centred local Confetti repair, lower-allocation clearance inspection, bounded Prismatic sheet scheduling, exact strand optimization. See `docs/engineering/M6-565-completion.md`. Full production and device qualification pending.

## M6 Confetti detail candidate — versionCode 563 (3.5.2)

Adaptive scattered fragments for all six Confetti styles; coarse detail remains unchanged. New host comparative and vector export/review gates. **Not physical cut-qualified.** See `docs/engineering/M6-563-confetti.md`.

## M6 reliability candidate — versionCode 562 (3.5.1)

Bounded physical repair grid, cut-share warning preservation, low-heap sequential geometry, photo-suggestion lifecycle handling, and cross-pipeline NAS preflight checks. Android build and physical qualification remain required. See `docs/engineering/M6-562.md`.

## M5 completion candidate — versionCode 561 (3.5.0)

The M5 artwork engines are finished on the host: Linocut prints like a relief block (solid shadows with carved gouges), Crochet is a cut-clean filet grid, Banknote Engraving keeps its tone at the 0.8 mm minimum, Confetti chooses its solid base to match the photo, and Prismatic Diagonal gains the size-aware pitch of its siblings. Registration marks can go above and below the artwork, so 11 in landscape art fits a 12 × 12 mat. Prismatic renders use about a third less memory, and geometry no longer depends on device speed.
Host-verified only (JVM gates, debug build): not yet run on a phone, in Design Space or on a cutter, so it is a qualification candidate, not a qualified release. See `docs/assessments/2026-11-25-m5-completion-report.md`.

## M5 beta 1: Prismatic and mat fit — versionCode 560 (3.4.1)

This is a beta of work toward M5, not the completed M5 release; versionName 3.5.0 is kept for that.
Five new Prismatic styles (Contour Flow, Twill, Silk, Braided Flow, Chevron) join Prismatic Diagonal. All six Prismatic styles now export cut files without cut-review findings in the benchmark, Diagonal included (it had about 300 per portrait).
The Cricut mat (12 × 12 or 12 × 24) is chosen in Size and saved with the project; exports that do not fit it are refused, and nothing is ever resized.
Host-verified only: not yet run on a phone, in Design Space or on a cutter.

## M4 Artwork Quality — versionCode 559 (3.4.0)

Photo palettes now come from a whole-image OKLab analysis (no duplicate or invented swatches, small accents kept), and ten new colour schemes are available.
Linocut follows the photograph's tones; Torn-Paper Collage and Marquetry no longer show base-colour hairlines after cut prep; cut prep states the holes it filled and pieces it removed.
Suite styles are designed to your material's weed gap, and ribbon, stipple and layered-cell sliders start at your material's minimum. Host-verified only: not yet run on a phone, in Design Space or on a cutter.

## M3 Editor State — versionCode 558 (3.3.3)

One `StyleSelection` value now decides the artwork style; a single method derives the old mode flags, so two styles can never be active together and undo returns to the previous style.
New Project resets every per-style setting (D19); the live colour preview never reaches saved settings (D15); a save finishing during dark-mode/locale recreation keeps its project id (D14).
Undo stays in the current workspace and selecting a layer keeps the zoom. Device-level behavior has on-device test cases that have not yet been run.

## M3 Reliability — versionCode 557 (3.3.2)

Damaged library projects remain visible for recovery/delete without becoming loadable.
Backup ZIPs are fully staged and CRC-verified before opening the SAF destination.
Provider-side copy atomicity and phone/Design Space/cutter tests remain unverified.

## Product — versionCode 556 (3.3.1)

Single-upload Cricut Design Space hand-off: each cut ZIP also carries one all-layers SVG (`00-ALL-LAYERS-one-upload.svg`), and the post-save share dialog can send just that file.

## Product — versionCode 555 (3.3.0)

Chainmail/Confetti/Fishnet/Crochet palette visibility fix (rings no longer vanish on shadowed or dim photos) and parallel vector geometry (about 19% faster overall, up to 35% on the slowest styles, identical cut output). See `docs/engineering/PRODUCT555-ENGINEERING.md`.

## Product — versionCode 554 (3.2.0)

Engineering follow-through: golden cut-sheet and render-time regression for every style, project format 2 (checksum manifest), cut sheets kept with each saved revision, per-sheet weed difficulty, camera capture, TalkBack/large-font improvements, gallery and UI-factory extraction, and 11 on-device test cases. See `docs/engineering/PRODUCT554-ENGINEERING.md`.

## Product — versionCode 553 (3.1.0)

Product-readiness release: fast gallery listing, phone-to-phone project transfer and backup reminders, share sheets to and from cutter apps, opt-in crash reports, photo/settings autosave pairing, engine-build stamps, zero compiler warnings, one shared gate manifest for every pipeline. See `docs/engineering/PRODUCT553-ENGINEERING.md` and `docs/ARCHITECTURE.md`.

## Precision — versionCode 552

Physical curve accuracy, actual stipple visibility order, vector contour counts and consistent material clearances. See `docs/engineering/PRECISION552-ENGINEERING.md`.

## Reliability — versionCode 551

Project transition ownership, immutable source staging, process-shared library, and worker-only archive opening. See `docs/engineering/RELIABILITY551-ENGINEERING.md`.

## Stability — versionCode 550

Render request snapshots, immutable mark revisions, portrait-cache isolation, bounded lifecycle state and undo memory.

## Workshop — versionCode 549

Specialty assembly measurements, revision-owned document progress, independent thin-vinyl width checks, and fail-fast host integration.

## Cut Intelligence — versionCode 548

Searchable offline help, slider ranges and conservative area-bound footprint diagnostics.

Search synonyms, local favorites and recent selections; existing style activation unchanged.

## Specialty Metrics Repair — versionCode 546

Contour counts and approximate lengths remain available above the footprint point budget. Dense spiral inspection no longer rejects by sample count. Curve sampling follows control polygon excess instead of segment length.

## Inspection Gate Repair — versionCode 545

Host vector gate includes Android graphics test stubs.

## Advanced Inspection — versionCode 544

Read-only footprint component diagnostics and Photo Spiral inspection.

## Universal Inspection — versionCode 543
Always-visible Cut Check measurements, specialty filled geometry and separate base support.

## Artwork Visibility — versionCode 542
Read-only union and stacked coverage diagnostics for standard artwork.

## Artwork Measurements — versionCode 541

Read-only standard artwork contour statistics.

## Calibration Profiles — versionCode 540

Named advisory records with create, edit, duplicate, delete and legacy copy. No rendering changes.

## Calibration Recorder — versionCode 539

Restores the visible Styles instruction and verifies both current and historical guide assertions.

## Smart Guide QA — versionCode 536

Explicit photo state, bounded routing, guide topic navigation and corrected handoff instructions.

## Smart Guide — versionCode 535

Simpler, context-aware help. New User Guide is 5 short pages; Tips & Tricks is 11 question-style topics. Opening either from the project menu starts on the page for the current workspace (and the current cutter on Export), and an Open shortcut jumps straight to that workspace. See SMART-GUIDE-ENGINEERING.md.

## User Guide Refresh — versionCode 534

Seven beginner pages and 14 directly selectable reference pages accessible offline from the project menu. Rendering unchanged. See USER-GUIDE-REFRESH-ENGINEERING.md.

## Portrait Detail Restore — versionCode 533

Restores cut-safe raised fish-scale accents, independent solid cell motifs and tone-aware camouflage compositing. See PORTRAIT-DETAIL-RESTORE-ENGINEERING.md. Android/device qualification remains required.

## Performance Gate Repair — versionCode 532

Repair RC173-W5 standalone javac source discovery. No production rendering changes.

## Performance Architecture — versionCode 531

Indexed contour-neighbor queries, exact geometry/motif caches, bounded independent stencil contour work, background palette count selection and per-style measurement registry. See PERFORMANCE-ARCHITECTURE-ENGINEERING.md. Android/device qualification remains required.

## Render Pipeline Optimization — versionCode 530

Exact Gaussian/percentile optimizations, bounded final image-field reuse, palette distance workspace and redundant Organic Flow draft removal. See RENDER-PIPELINE-OPTIMIZATION-ENGINEERING.md.

## Smart Palette Reduction — versionCode 529

Shared perceptual palette selection preserves original vinyl swatches and role constraints as layer counts change. See SMART-PALETTE-REDUCTION-ENGINEERING.md for scope and qualification limits.

## Ribbon Color Coherence — versionCode 528

Local Woven Ribbon swatch runs, physical-order-aware crossing masks, and jointly refined Prismatic ribbon colors with linear-light coverage prediction. See RIBBON-COLOR-COHERENCE-ENGINEERING.md.

## Organic Tone Band Repair — versionCode 527

Protect each Organic Flow tonal band from unrestricted adjacent color reassignment, retaining perceptual matching at band boundaries. Verify all six physical sheets at three sizes and three smoothing levels. See ORGANIC-TONE-BAND-REPAIR-ENGINEERING.md.

## Swatch Gate Repair — versionCode 526

Reconciles Woven Ribbon’s intentionally changed photo-driven geometry with the selective ridge regression. Production Java sources identical to build 525. See SWATCH-GATE-REPAIR-ENGINEERING.md.

## Perceptual Swatch Repair — versionCode 525 (qualification candidate)

Nine-slot named palettes, nested tonal direction, bounded perceptual selection, and photo-driven Woven Ribbon.

## Prismatic Vector Boolean — versionCode 524 (qualification candidate)

Direct continuous ribbon outlines on each physical Prismatic sheet, fixed-precision union, protected Snip clipping and locked final geometry. Existing palette/controls and interactive draft retained. See PRISMATIC-VECTOR-BOOLEAN-ENGINEERING.md. The production JTS and Android gates must pass before publishing.

## Layer Vector Boolean — versionCode 523 (qualification candidate)

Direct, independent physical-sheet vector unions for Confetti, Chainmail and Fishnet. JTS 1.20.0 fixed precision; mandatory NAS/CI regression tests. Production backend and Android/device qualification remain pending. See LAYER-VECTOR-BOOLEAN-ENGINEERING.md.

## Selective contour polish — versionCode 522

Conservative final-geometry smoothing for Round and Streamer Confetti, Chainmail and Fishnet. Preserves sampled sheet masks, cut-loop count, base silhouette and protected Snip boundaries. See SELECTIVE-CONTOUR-POLISH-ENGINEERING.md.

## Prismatic rounded ribbons — versionCode 521

Targets the sharp tapered ribbon terminals, angular contours and narrow brown palette tonal compression reported on device. Rounds transitions, increases density and bounded final trace resolution, uses lightness-adaptive palette scoring without editing physical sheets, and preserves the existing release gates. See PRISMATIC-ROUNDED-ENGINEERING.md.

## Release gate repair — versionCode 520

Preserves build 519 rendering and adds missing Play bundle gates; validates the current thumbnail registry against the historical retirement contract.

## Prismatic weave — versionCode 519

Replaces inset diamond-cell motifs and parent knockouts with two photo-guided continuous serpentine ribbon families. The solid Snip background and physical slots are maintained, with independent masks and opaque topmost-sheet color. Same-sheet tiny enclosed pinholes are cleaned. Existing saved style ID and controls retained. See PRISMATIC-WEAVE-ENGINEERING.md. Host test gate covers the style, sliders, actual final-vector overlap, different palettes and sizes. Android/device/physical cut qualification remains pending.

## Slider, Overprint and Snip correctness — versionCode 518

- Preserve independent sheet settings when master controls are changed. When unlinked,
  master sliders are replaced by a visible explicit relink action.
- Map tessellation/cell gap to the real material-safe minimum, and always send
  the selected canonical value to specialty preview/export.
- Display slider values with their actual increment precision (0.05 -> 0.25,
  rather than 0.3). Fix the inch-slider's initial readout to match its thumb.
- Allow separated Overprint marks to reuse one color sheet while preventing
  touching/intersecting same-sheet duplicate cuts.
- Sample from valid Snip pixels when arranging a photo-driven Overprint palette;
  make cell gradient orientation ignore pixels excluded by Snip.
- Run `tests/run-v3-slider-overprint-repair-gate.sh` alongside inherited suites.

**Qualification:** Host tests pass; Android compile/lint, phone performance and
physical Cricut SVG/DXF import/cut testing still require the configured NAS and device.
The legacy RC124 reverse-sheet fixture already fails on unmodified build 517;
this change does not address historical palette policy differences.

## Consensus color and render-speed repair — versionCode 517

See `docs/engineering/CONSENSUS-COLOR-SPEED-ENGINEERING.md` for changes, research rationale, tests, and remaining device validation.

- Reuses the previous build 516 Overprint repairs; makes photo-driven L1 color
  follow the photograph’s restrained shadow range instead of a fixed swatch rank.
- The physical overprint optimizer gives more weight to luminance fidelity,
  prepares immutable palette coordinates once per generation, and precomputes
  candidate region/color losses per cell for faster interactive rendering.
- Reuses a single Snip coverage check for every mark in the same cell, and adds
  Perfetto trace sections to profile actual Android render latency.
- No new rendering mode, per-layer translucent blending, cutter changes,
  or auto-mutation of saved swatches.
- Mandatory `tests/run-v3-consensus-color-speed-gate.sh` guards those changes.

- All four retained opaque Overprint cell styles score actual visible upper-sheet
  ownership in perceptual OKLab instead of channel-weighted linear RGB. Solid
  L1 remains the structural foundation; invalid Snip pixels cannot assign marks.
- Explicit photo/preset selection now chooses the most useful 2–8 active rolls
  out of all eight swatches. The original eight colors survive; inactive slots
  remain parked. Flat photos and absent photo data have deterministic fallbacks.
- The initial Overprint style selection arranges the current eight candidate
  colors once. Photo Suggestions, mosaic presets, and the direct palette dialog
  use the same active-color policy. Manual edits, saved palette order, Hue and
  mounting-surface changes never trigger invisible reordering.
- Rasterized preview and export composites paint geometry in physical
  bottom-to-top layer order (L1→L8), matching the live vector view and SVG
  assembly. Cut outlines, Snip geometry and style IDs remain unchanged.
- Mandatory `tests/run-v3-overlay-swatch-gate.sh` covers four styles, physical
  visibility, photo sensitivity, saved/reversed palette preservation and stack
  identity. Android compilation, device QA and test cuts are still required.

## Wolfram mathematical repair — versionCode 515

Source fixes and host reproductions for (1) under-sampled opaque overprint
accents, (2) non-finite Snip inputs, (3) registration-aware mat sizing in
Prepare → Size, (4) independent crop-edge rounding/metadata parity, and
(5) square/circle crop aspect preservation on unusually tall/wide photos.
The inactive legacy wave-frequency expression is corrected but the active
LineArtEngine wave style has not changed. Existing style IDs, preset defaults,
registrations and saved projects remain intact. Older RC146 static assertions
now inspect the current staged export handoff instead of obsolete call text.

`tests/run-v3-math-repair-gate.sh` is the mandatory fast regression; the slower
`tests/run-v3-math-repair-extended-gate.sh` adds broad exports and registration.
An Android SDK/device and real mat cuts are still needed for release qualification.

## Overlay swatch stack — versionCode 514

Overprint Orbits, Lozenge, Diamonds and Mixed Marks now fit actual opaque
bottom-to-top vinyl colors against the photograph, with a structural L1 and
optional omitted marks when a sheet would worsen the visible portrait.
The Colors and Layers panels show final visible-area estimates from the actual
clipped output polygons. Built-in and saved palette previews now map to the
same physical swatches that are committed: saved palettes are never re-sorted,
Reverse leaves L1/parked slots in place, and manually edited colors remain
unchanged when only the mounting surface changes. Play, NAS, CI and Gradle
host gates include the new swatch tests. Retired styles remain retired.

## Render speed audit — versionCode 513

Based on Claude render code.zip, reviewed and integrated as an Android development candidate:
parallel independent color layers (bounded helper queue), interrupt-aware task cleanup,
coalesced luminance-plane cache protected against duplicate computation and invalidation races,
loop-local cancellation checkpoints, and normal-priority render lanes. Geometry algorithms
and previously retired style IDs remain unchanged. The render-speed parity and concurrency
tests are mandatory on NAS, in CI and in the optional Gradle host suite.

## Style retirement — versionCode 512

The user removed **Painterly Cell Weave** and **Ridge Lines**. Both are omitted from the picker, thumbnail registry, and active selection routes; legacy numeric IDs stay reserved and cannot be reused. The Painterly Cell Weave renderer now rejects its historical kind 5; `VinylStyleSuite.RIDGE` style ID 7 is retired. When old projects open, the loaded photo and Snip are preserved but neither removed engine is activated; saving normalizes to photo mode.

Preserved: Overprint Orbits, Lozenge, Diamonds, Mixed Marks, Topographic Layers, Banknote Engraving, Fingerprint Portrait, Flowing Woodgrain and optional organic-ridge *smoothing* applied to those unrelated styles. The earlier Painterly and selective-ridge notes below are historic records, not currently selectable styles.

The thumbnail registry has 76 top-level and 28 tessellation identities. Run `bash tests/run-v3-style-retirement-gate.sh` followed by the regular NAS/Android gates.

## Painterly Cell Weave rebuild: photo-fitted brush field (versionCode 511)

- Preserves saved style ID `multilayer.painterly_cell_weave` and all four original Overprint engines.
- Replaces preset mosaic cell geometry and separately traced facial outlines with a global, multi-scale photo-reconstruction objective. Every opaque organic mark must measurably reduce source-photo error before being accepted.
- Photo-derived regional underpaint, broad-to-fine curved paint swells, 2–4 sampling-cell spans and reserved facial-detail stroke budgets.
- Original image data drives tone and marks before Snip boundaries are applied to vector geometry. The virtual grid controls sampling and scale but never bounds a motif.
- Painterly texture, stroke-detail passes, and photo-structure sliders are now functional. Layer 1 remains a solid Snip silhouette; photo palettes, layer order, minimum cut widths, registration, and SVG dimensions are preserved.
- Measured on a screenshot-derived low-resolution portrait crop with an approximate palette: source-vs-render RGB RMSE decreased from 37.36 (build 510) to 22.47 (this source). This does **not** validate artistic resemblance to an original Chuck Close artwork or a phone render using the full-resolution photograph.
- Host tests: deterministic geometry, 2–8 layers, 3–11-inch sizes, Snip, physical gaps, SVG per layer, regression checks for the existing four styles.
- Android Gradle build, physical cutter verification, and full inherited Beta4 gate still require separate qualification.

## Painterly Cell Weave fidelity repair (versionCode 510)

- The original Painterly Cell Weave's dense uniform dot field and ungrounded long strokes are replaced with a tone-adaptive paint-like cell field, with 7x7 mask-aware photo sampling per cell and local dark/light quantiles.
- Sparse, multi-scale photographic dark landmarks preserve pupils, nostrils, eyelids and lips as solid connected shapes spanning multiple cells. The darkest eligible physical upper sheet is used, and overlapping higher sheets are omitted rather than creating tiny weeding holes.
- Long bridge marks now require macro-scale persistent photo gradients, use smaller strokes, and are limited to 11 per portrait. Flat input produces neither bridges nor invented dark landmarks.
- Same-sheet cell envelopes have a full vertex-level cut clearance, unchanged cross-sheet overlaps, SVG dimensions, Snip mask, registration, and cut-safety validation.
- All four previous Overprint styles, stored style IDs and project JSON fields are unchanged. Saved Painterly Cell Weave projects will re-render with this corrected engine when opened.

## Overlapping cell vinyl portraits (versionCode 508)

Four styles: **Overprint Orbits**, **Overprint Lozenge**, **Overprint Diamonds**, and **Overprint Mixed Marks**. Each has a distinct picker thumbnail and preset geometry, shares a common photo-adaptive renderer, and is fully independent from legacy nested Layered Cell, Offset Cell, Fish Scale, and Bottle Cap renderers.

- **Opaque physical layer stack:** L1 always remains a solid Snip silhouette. L2+ comprise independently offset, overlapping photo-colored cells. Cross-sheet overlaps are intentional; same-sheet isolated cuts retain configured spacing.
- **Color and quality:** photo-aware linear RGB nearest-palette matching, gradient-sensitive placement/rotation, cell-density, photo-detail, artistic-overlap, and motif-depth controls. Custom color edits and palette order continue through existing layer controls.
- **SVG:** color-isolated contours in millimeters with the same registration/Snip/export safety pipeline. No new nested upper-layer cutouts. Existing project JSON remains backward compatible; new style selection uses the `overprintCellKind` property.
- **Cut-safety:** per-sheet clearance validation, minimum feature tests, Snip clipping and cut contour budget, stable deterministic output. Legacy cell geometry unchanged.
- **Proof:** `bash tests/run-v3-overprint-cells-gate.sh` exercises all four styles at eight layers with and without Snip, two/three-layer edge cases, deterministic vectors, SVG/sheet safety, and selection wiring. Host tests do not replace Android/NAS compilation or physical cutter testing.

## Selective ridge contour polish (versionCode 507)

- **Fingerprint Portrait and Flowing Woodgrain Portrait only:** Optional, local, cut-checked secondary boundary polish for continuous curved ridges. No added slider. Maximum eight long contours per color layer; unfit or dangerous candidates retain the prior polygon.
- Rejects deformation that alters winding, enclosed area, minimum cutter clearance, or local cut-warning locations. The same polished vector coordinates are used by final preview and SVG/DXF export.
- **Not changed:** Organic Marquetry, Reduction Woodcut, Woven Ribbon, Torn-Paper Collage, geometric/tessellated styles, and the existing three Organic Flow 50%-smoothed styles.
- Verification: focused 3/4/11-inch geometry contract, 4/11-inch SVG exports, current 34-style SVG/DXF matrix, and release scripts. Full Android/device and physical cutting require NAS/device qualification.


## What changed in this candidate
- **Contour-local safety fast path:** on proven isolated changed boundaries inspect only the changed loop at the stricter of minimum width/gap; uncertain interactions fall back to the original two-page differential review. Changed-loop bounds are cached for each layer.
- **Primitive cut-safety spatial buckets:** ordered primitive arrays replace boxed Long / ArrayList hash buckets without changing the contact predicates, warning order, or cut thresholds.
- **Edge-map percentile selection:** a bounded three-way quickselect obtains the identical 97th-percentile float without sorting the entire gradient array.
- **Cached immutable photo analysis:** one exact-content-checked cache of luminance, Snip-validity, blur and edge maps; photo, Snip, size or lighting change invalidates automatically. Flow and palette changes can reuse it.
- **Reduced repeated per-pixel reads:** high-pass tone / local contrast / edge protection reuse the same low-frequency sample.
- **Fixed 50% Organic Flow smoothing, protected solid L1 and draft/final isolation remain as in code 505.** The other styles, Poster Stencil nine-layer controls, and the saved-project schema are unchanged.
- **Mandatory high-gain optimization gate** tests percentile bit parity, photo-cache invalidation, safe isolated-contour acceptance, three-style geometry fingerprints and geometry-warning parity.
- Parallel layer rendering, distance-transform morphology and numerical `Math.hypot` replacements are deliberately deferred pending their own device/cancellation/safety qualification.


**Poster Nine Layers (code 492):** Pop Panels (historical ID 46) retired from the
Styles picker; saved Pop artwork falls back to photo/Prepare with its photo and
Snip intact. Poster Stencil keeps ID 47 and uses the ordinary nine-physical-slot
Colors/Layer pages, with an undeletable L1 Snip silhouette and L2–L9 tonal
plates. Native color-wheel edits, Hue, Reverse, photo colors, material and
palette selection directly feed preview and SVG/DXF. Legacy Poster-specific
saved swatches migrate once into the physical slots. Host gates validate SVG
and DXF sheets, but Android/device/Cricut qualification is pending.

**PopPoster Fix1:** repairs first-time style-selection crashes for Pop Panels (ID 46) and Poster Stencil (ID 47) by extending PhotoStyleDefaults tables. New `V3StyleSelectionCrashRegressionTest` exercises every selectable style at three physical sizes. Rendering and export geometry are unchanged; see POP-POSTER-FIX1.md.


**Pop Panels / Poster Stencil (new):** photo labelers ported from the supplied JS reference to two independent Java styles, palette-controlled 2x2–3x3/1x1–1x4 Pop grids, 2–5-tone posters, stacked/knockout vinyl, editable custom colors, generated paths, Snip-after-tone, Android text masking, and project save/restore state. Per-color filled polygons use the existing physical SVG/DXF export writer and safety checks. Validation: `bash tests/run-v3-pop-poster-gate.sh`. See POP-POSTER-IMPLEMENTATION.md for limitations.


**OrganicFlow Fix1:** Corrects stale tessellation/thumbnail QA snapshots for 99 current identities; the three engines and previously accepted cut geometry are unchanged. Full NAS Android build remains required.

**OrganicFlow (retained):** Three selectable photo-aware multilayer vinyl styles: Quilling Ribbons, Marbled Ink and Quilled Marbling. They share the standard final-vector preview/export pipeline; protect important tonal/feature boundaries, provide physical solid Snip L1, and automatically constrain ribbons to the selected minimum cut width and gap. New independent Java render and SVG cut-safety tests: `bash tests/run-v3-organic-flow-gate.sh`. Does not re-enable Puzzle Portrait. Android device and physical cutting are not qualified.

**Fix15 TonalLightness:** Tonal Stencil preset colors are now assigned darkest-to-lightest to photographic source IDs, rather than the separate coverage-driven physical sheet order. Photo colors resets all preset overrides and exits Match-my-vinyl mode; named/saved physical palettes and manual Reverse remain explicit. No ownership masks, Snip paths or cutting dimensions are changed. Gate: `bash tests/run-v3-fix15-tonal-order-gate.sh`. Host source candidate; Android/device checks pending.

**Fix14 TonalPaletteStyles:** Fixes source-color-ID versus physical-stack ordering in Tonal Stencil palette selection, active chips, Hue/Reverse, named palette save/reapply and layer cards. Changing palette no longer assigns physical L1 to an unrelated photographic source index. The picker tab is now **Styles** and its geometric-shape group remains distinct. Gate: `bash tests/run-v3-fix14-tonal-palette-gate.sh`. This is a source QA candidate, not a device-qualified APK.

**Fix13 PostSnipVectors:** A Snip no longer disables classic-CMYK line highlight/retention lanes or switches modern line subject/background heuristics. Existing crop/photo colors and manual spacing/width settings are preserved on reframe. Interior generated SVG ribbon occupancy is regression-tested across classic/modern LINE/CONTOUR/HATCH at 0.125 and 0.245 inches, including alpha crop. Hard Snip exclusion, minimum cut widths, and final-sheet export inspection remain mandatory. Host gate: `bash tests/run-v3-fix12-crop-parity-gate.sh`. See `docs/engineering/FIX12-CROP-PARITY.md`.

Fix10 GeometryOptions repairs the default “Invalid vinyl geometry options” failure in Layered/Offset Cell, Bottle Cap and Fish Scale styles and aligns the mosaic and cell validators with material-profile gaps. Regression gate: `tests/run-v3-fix10-geometry-options-gate.sh`. See `docs/engineering/FIX10-GEOMETRY-OPTIONS.md`.

Fix9 accepted-project state and Hue/Reverse repairs: see `docs/engineering/FIX9-COLOR-STATE.md`.

**Fix8 CutTune:** every style is tuned to export cut sheets that pass the export inspector at the app defaults (0.8 mm minimum cut width, 0.5 mm minimum gap). See `docs/engineering/FIX8-CUT-TUNE.md` and `tests/run-v3-fix8-cut-tune-gate.sh`.

Beta 21 calibrates each filled Shapes motif against the historical Round area response,
with restrained dark-tone filling for five-point Stars and unchanged center
spacing. The min-width and cell-clearance constraints remain enforced. This
changes vinyl cut geometry, so production cutting requires scrap-cut testing.
Round, Lines, ASCII and Single Line geometry are unchanged (frozen host hashes).
See `docs/engineering/V3-BETA21-SHAPE-TONE-NOTES.md` and `tests/run-v3-shapes-area-tone-gate.sh`.


**Beta 26 all-style export audit:** Every selectable new-style family is exercised through final-sheet SVG and DXF R13 export (30 families, 196 physical-layer/format exports at default settings); seven cutter output profiles are checked separately. Specialty and silhouette cut archives are now prepared, safety-reviewed, CRC-verified and then offered to Android's Save picker. Cutter-specific path limits no longer apply to other profiles. Stained Glass padded raster coordinates, Linocut edge overshoots, and Topographic loop boundaries are corrected with fail-closed repair. An explicit diagnostic ZIP records source-level concerns even if the physical final sheet has no additional warning. This does **not** certify other setting combinations, hardware import, cutter registration or physical cutting. Host gate: `bash tests/run-v3-beta26-export-matrix-gate.sh`.

## Beta 25 — Cut-safe Snip base and compact circular registration

11-inch Stars exported successfully in Beta 24, but the solid Snip base was UNVERIFIED (48 narrow findings) and the physical artboard measured 315.544 mm, too wide for the Cricut Maker. Beta 25 optionally simplifies the traced Snip contour by at most 0.35 mm; it accepts the result only after cut-width and contact inspection and strict per-ring area/orientation checks. Default circular-crop registration moves into verified empty corners and reduces unnecessary padding, preserving the same absolute artwork-local mark centers on all physical sheets. Manual marks and noncircular artwork remain unchanged. The shared cut ZIP writer fails Cricut-only output exceeding the physical Maker mat size; no SVG is silently rescaled. Host tests: `bash tests/run-v3-beta25-snip-mat-gate.sh`. Real phone and Cricut import tests remain required.

## Beta 24 — Verified before the Save picker

Standard SVG/DXF cut ZIP export is now generated, inspected, CRC-verified and staged on private disk BEFORE the Android document picker appears. Unsafe or unverified cut geometry presents a persistent inspection-only choice before file creation. If the user cancels or validation fails, no zero-byte ZIP is created. The optional solid Snip base-only export is preserved. Provider write failures are reported and the newly created document is deleted where the provider allows. Diagnostic ZIPs include NOT-CUT-QUALIFIED.txt for critical, narrow, incomplete, or source-level findings. This is not physical cutter certification. Run `bash tests/run-v3-prepared-export-gate.sh`. See `docs/engineering/V3-BETA24-PRESAVE-EXPORT-NOTES.md` for failures and open qualification gates.

## Historical Beta 20 release hardening

Beta 20 hardens staged cut ZIP publication, rejects mixed cutter formats, requires final unverified geometry review, and rejects corrupt or empty saved-project payloads. It preserves existing motif geometry, palettes, cut dimensions and migration IDs. See `docs/engineering/V3-BETA20-RELEASE-QA-NOTES.md`.

## Previous changes — Beta 17 A/B handoff and C1

New: conservative LINE/CONTOUR local crossing repair (unfixable cases remain fail-closed), standard mixed circle/polygon clearance, per-sheet `cut-complexity.csv` and Cricut element budget. B2 real-photo qualification worksheet added; physical cuts and Android SDK build are still required.

# Historical Beta 16 — Milestones A4 / B2–B4

**Beta 16 historical candidate:** `versionCode 467`, `versionName 3.0.0`,
APK `VinylHalftoneStudio-v3.0.0-Beta16-ABQuality.apk`.

This release builds on A1–A3, B1 and Beta 14 Shapes sampling with bounded transverse edge-bleed suppression for modern Lines. It retains physical dimensions, exact swatch values, saved IDs, and the existing LineArtEngine path. A1–A3 remain retained. All SVG/DXF cut ZIPs now enforce matching physical sheet artboards;
the final-sheet review checks circle-to-polygon clearance and out-of-artboard
contours. It also writes `physical-artboards.csv` to each cut ZIP. The existing
Snip clipper is unchanged; new topology/area fixtures protect it against future
regression. Run `bash tests/run-v3-milestone-a1-gate.sh` and `bash tests/run-v3-milestone-a2-gate.sh` for local checks.
The full inherited host suite and Android/NAS/device qualification remain separate.
**Not physically certified** until imported and cut on target hardware.

**Milestone B2:** The Shapes motif sampler now retains center tone at actual photographic transitions, scales by physical millimeters, rejects transparent-center islands, and leaves tessellation engines unchanged; the separate B2 Lines edge gate changes only modern carrier tone and leaves classic CMYK geometry frozen. Run `bash tests/run-v3-milestone-b2-gate.sh`. See `docs/engineering/MILESTONE-B2-ENGINEERING-NOTES.md` and `docs/engineering/MILESTONE-B2-LINES-ENGINEERING-NOTES.md` for synthetic 6/11/15-inch host tests and the device A/B protocol.

---

## Beta 16 A4 / B2–B4 software changes

Long-line crossing findings and scan-budget exhaustion fail closed on cut export, even after safety acknowledgement. Source-color swatches are never rewritten. High Detail now caps the tonal stencil blur footprint in real-world millimeters. Manufacturer palette samples (ORACAL 651 adhesive / Siser EasyWeed HTV) are explicitly reference sRGB only; a printable assembly checklist lists colors and bottom-to-top application order. See `docs/engineering/MILESTONE-A4-B2-B4-ENGINEERING-NOTES.md`. Run `bash tests/run-v3-milestone-ab-completion-gate.sh`. No cutter certification or real-portrait A/B results have been obtained yet.

## Beta 9 Tonal28 — perceptual tessellation photo-to-swatch mapping

Use the Fix9 ColorState TransferKit for this Android development candidate (v3.0.0, versionCode 482).
The 28 active tessellation patterns have style-aware tone mapping. No previously
retired pattern is reintroduced. The new mapping uses perceptual Oklab rather
than weighted RGB distance, honors the solid silhouette base as a real vinyl
color, and applies only bounded/conditional tonal range normalization.
Neither the colors nor the physical layer order are automatically modified.

**Qualification:** `bash tests/run-v3-beta9-tonal-gate.sh` and
`bash tests/run-v3-beta4-host-gate.sh`, followed by Android JUnit/lint/assemble
and real cutter software/physical output tests on your NAS and S22 Ultra.

## Beta 8 Trim5 — five user-retired reference tessellations (development only)

Retired IDs 24, 27, 32, 33 and 36 (Interlocking Dumbbells, Diamond Star Lattice, Circle Chain Lattice, Diamond Arc Rosette, Octagon Diamond Links). **28 selectable tessellations remain; all 37 saved IDs stay stable.** Opening retired IDs selects Trihexagonal while preserving the original photo and Snip. Run `bash tests/run-tessellation-reference-gate.sh` before Android build. Actual vinyl cuts, cutter import and Android lint are not verified in this container.

---

## Prior high-detail photographic tessellations (Beta 6)
Seven reference layouts (saved IDs 22–28) now produce substantially finer photographic geometry at the existing maximum Detail value. The new density is progressive from 0 to 100 and only applies to those seven layouts. A mask-aware cell budget no longer wastes most of the available tile allowance on Snip-excluded background. Photo color sampling gently favors the centre of each motif at higher Detail. IDs 0–17 and the retired 18–21 maintain their existing behavior. No new sliders, colors, sheets or project migration required.

**Important:** Increased density does not imply physically qualified cuts. Boundary cells still use the existing pixel-based Snip clipper; low-resolution photographs or narrow color regions can produce close, touching or overlapping contours that need the existing cut-safety report and a scrap cut. Physical cutter import has not been qualified.

# VinylStudio 3.0.0 — Engineering history

Offline-first Android app for physically cuttable multilayer vinyl halftones, stencils, tessellations and line artwork. **Development source; not a Play-approved production release.**

- **App version:** 3.0.0, `versionCode 496` (monotonic from W5 code 451).
- **Immutable Android package identity:** `com.halftonestudio` — deliberately retained for in-place upgrades signed with the same key.
- **Product display name:** VinylStudio.
- **Baseline:** RC173-W5. Existing style IDs, palettes, SVG/DXF cut outputs and project schema are preserved.
- **Current expected development APK:** `VinylHalftoneStudio-v3.0.0-OrganicSlider.apk`.

## What changed in v3 Beta 1 (retained)

The project archive reader now streams `photo.png`/`photo.jpg` and optional `original.png`/`original.jpg` into private, size-bounded temporary files. It checks decompressed ZIP sizes, entry names, image structure and PNG chunk CRC as bytes arrive. The editor decodes bounded previews from disk, stages the exact encoded original for resaving, and deletes temporary files after decoding, canceled loads or failures. An age-limited janitor reclaims crash-orphaned temporary files after 24 hours; active files are retained.

Previews now enforce **both** dimensions and maximum pixel budget. Manual project saves warn when a cached native camera file exceeds the portable ZIP limit of 20 MB. The project photo and encoded full-resolution original are still saved according to the existing project format.

This is **not** a rewrite of the cut engines or a promise of crash-proof autosave. W5's contour intersection detection and project-load ownership fixes remain in place; no geometry tolerances or physical-layer semantics have been changed in Beta 1.

## Beta 3 cut-safety and archive integrity changes

- Logical ZIP roles are unique across extensions: an archive cannot contain both `photo.png` and `photo.jpg`, or both `original.png` and `original.jpg`. The streaming importer, legacy importer and both library verifiers now share the same entry policy. Rejected staged projects clean up their temporary files.
- Rotated narrow four-sided vinyl motifs are detected by geometric minimum width, not just axis-aligned bounds. The cut preflight compares independent edges even when they are two positions apart in a four-vertex loop.
- Before new-style cut ZIP publication, physical-cut warnings require explicit review/acknowledgement. Contacts, overlapping edges and incomplete scans are dangerous; narrow widths are warned. All findings remain in `cut-safety-review.csv`. Cancelling avoids publishing artwork bytes but a document shell may already have been created by Android's picker.
- These changes do **not** constitute real cutter qualification, fix process-kill atomicity of the photo/original pair, or remove the Play privacy-policy requirement.

## Beta 4 final-cut safety and crash-recovery qualification

- Added **final-sheet geometry checks** at the centralized SVG/DXF cut ZIP writer used by all four cut-output routes. Scans actual physical sheet SVGs before DXF conversion, without changing cut geometry. Detected intersections/contacts require the existing review confirmation; unsupported or budget-exhausted cases are explicitly **UNVERIFIED** in `cut-sheet-safety.csv`, not certified safe. Circle-to-polygon proximity remains unverified. A user choosing to continue after acknowledgement has not proved machine safety.
- Improved DXF R13 output with structural sections, handles, model-space ownership and millimeter metadata; independent CAD parsing succeeded for calibration fixtures. Actual Silhouette Studio Basic import dimensions and registration require field testing, including separate imported layers.
- Added executable **separate-process termination tests** for in-progress photo revision writes and an in-memory queued-but-not-staged save. The revision pointer recovers the last committed pair after simulated abrupt termination. **Queued-only saves do not survive process termination**; this remains an open durability requirement.
- Added two-layer SVG and DXF calibration archives, 100 mm reference artboard, measured registration mark spacing and central shapes. Registration marks are physical alignment artwork, **not** optical print-and-cut marks.
- Added `scripts/QUALIFY_ANDROID_DEVICE.sh`, a **read-only** ADB diagnostic collector and manual API 30–36 and low-memory test checklist. Neither Android build nor hardware qualification has been executed in this container.

## Build and test

```bash
bash tests/run-v3-host-gate.sh
bash BUILD_ON_NAS.sh
```

On the NAS, `BUILD_ON_NAS.sh` compiles Android sources, runs `testDebugUnitTest`, `lintDebug`, and `assembleDebug`, and publishes the verified debug APK only on success. It requires Android SDK API 36 and Java 17+. When `VINYL_QUALIFY_RELEASE=1`, it also requires a real `VINYL_PRIVACY_URL` and executes release unit tests, lint and R8 assembly. The separate Play AAB script additionally checks signing and fetches the public policy page.

**Phone workflow:** unzip `VinylStudio-V3-Beta25-SnipMat-TransferKit.zip` into Termux Downloads, then run:

```bash
cd ~/storage/downloads
bash VinylStudio-V3-TransferKit/VERIFY_PACKAGE.sh
bash VinylStudio-V3-TransferKit/PHONE_BUILD_AND_RECEIVE.sh
```

If the NAS succeeds but the APK is not received automatically, use `PHONE_RECEIVE_ONLY.sh` **after verifying NAS success**.

## Release qualification required

A published HTTPS privacy policy and correct Play Data safety statement are still missing. Tests must also cover an Android 30–36 inset/keyboard matrix, process recreation with a project open, low-RAM phone, R8 release APK, accessibility, physical multilayer cut safety, and Silhouette Basic DXF scaling/registration. Do not claim these are verified by host tests. The Gradle privacy release gate must not be bypassed.

See `docs/engineering/V3-RELEASE-PLAN.md` for deferred architectural work, `docs/engineering/V3-DEVICE-TEST-PLAN.md` for qualification, and the historical RC170–RC173 documents for prior engineering decisions.

## Beta 18 / Milestone C2 (historical)
Preview-only memory improvements: direct bounded ribbon-mask compositing, reuse of Android Path objects for Cell/Tessellation/Standard composite, and cooperative preview cancellation. Physical cut geometry and material selection unchanged. See `docs/engineering/V3-BETA18-C2-ENGINEERING-NOTES.md`.


## Beta 22 — Dense Shapes SVG ZIP export hardening

Dense final SVG cut sheets now use a bounded physical spatial-tile inspection
instead of marking every sheet exceeding 3,500 closed contours unverified.
Each tile uses the same geometric collision/clearance oracle as the existing
cut-safety gate. Budget exhaustion and unsupported geometry stay UNVERIFIED.
When inspection cannot be completed, standard Shapes export offers an explicit
**inspection-only** ZIP with a prominent `NOT-CUT-QUALIFIED.txt` warning;
this is not permission to manufacture unverified geometry. The user's
existing Round/Stars style settings, color ownership and SVG contours are
unchanged. Run `bash tests/run-v3-dense-export-gate.sh`.

## Fix11 — Snip-safe portrait detail (code 483)

Standard CMYK engraving and line engines now preserve the same midtone/highlight
response inside a Snip as they use without one. Gradient-dependent portrait
priority and ribbon-side bias skip samples that would cross a Snip edge. The
existing exact polygon cut boundary, physical min width/gap, registration and
source/photo persistence code is unchanged. Regressions run through
`tests/run-v3-snip-detail-gate.sh` before the long style matrix.

This is a host-verified source candidate, not a phone-qualified APK. Compare
identical source photo, geometry, Snip and material settings before cutting.

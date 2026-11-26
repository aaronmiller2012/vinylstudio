# Roadmap (from the 2026-11-21 assessment)

Defect IDs refer to `docs/assessments/2026-11-21-engineering-assessment.md`. Prefer fewer, complete, qualified releases.

## M0: Safety net

- [x] Off-machine remote: private GitHub repo `vinylstudio` (`origin`), all branches and rollback tags pushed (2026-11-23).
- [x] Fix D1 (corrupt autosave quarantined). Branch `export-sheet-contract`.
- [x] Real-engine export fixtures (`V3ExportSheetContractTest`, mandatory gate).
- [ ] Decide `slider-guide` and `export-sheet-contract` → `main`.

## M1: Cut-file correctness (in progress)

- [x] ADR-0001 step 1: `SheetFrame`, which fixes D2, D3 and D4 for every exporter family, the guide and the PDF.
- [x] Mat requirement stated in every Cricut ZIP (corrected D6).
- [ ] Device and Design Space check: import the colored per-layer and combined SVGs; record layer/mat split and dimensions in `qualification/`.
- [ ] Physical 3-layer registration cut at 3 in and 10.5 in, rectangular and circle crop.
- [ ] Show the required mat in the post-export dialog (the ZIP note exists; the UI doesn't show it yet).
- [ ] D8: slider bounds from the material profile.
- [ ] ADR-0001 step 2 (`ExportSheet`): one source for order, label, file name and color. Fixes manifest L-numbering, stale layer names and combined-file stacking order.
- [ ] Product decision: keep the 11 × 14 and 12 × 16 presets, which no Cricut mat accepts with marks on?
- Exit criteria:
  - [x] the fixture matrix is green;
  - [x] physical geometry is unchanged for rectangular crops;
  - [ ] the Design Space import is recorded;
  - [ ] the registration cut is recorded.

## M2: Preview = cut, visible feedback (implemented on branch; device unverified)

- [x] D5: mounting surface is display-only; explicit separation background (ADR-0003).
- [x] D7: cut-ready stage; preview = export for suite styles and stipple (ADR-0002).
- [x] D9: visible status line policy; no raw exception text.
- [x] D11: exact intersection check; indeterminate fails closed.
- [x] D13 evaluated: a higher source cap gives no measured geometry gain, so the cap is kept. Snip mask edge quality at large sizes is open.
- [x] Smoothing audited (`docs/assessments/2026-11-22-m2-rendering-report.md`). Exported holes and outer rings are consistent.
- [ ] Open: Torn Collage seam damage from per-sheet repair (product and cut-safety decision); suite `gapMm` vs `bridgeMm` (golden re-baseline); silent hole filling by the conditioner.
- [ ] Device: cut-ready latency, battery, status line layout, stipple stage.
- [ ] Declared render inputs per style (a general cache-key contract). Only the D5 instance is fixed.

## M3: Editor state and reliability (557 + 558; host-verified, device unverified)

- [x] D10: unreadable projects stay listed, recoverable as exact bytes, deletable (557). Bit-rot and malformed-ZIP cases (557).
- [x] D16: library backup staged and verified before the SAF document opens (557).
- [x] RC170 truncation requirement proven behaviorally (`LibraryDocumentTruncationTest`, 557).
- [x] Single `StyleSelection` replaces the independent style flags; one write path (558, ADR-0004).
- [x] D15: the layer colour dialog previews without touching settings (558). Other dialogs already commit only on Apply.
- [x] D19: New Project restores the fresh-install editor; cutter and vinyl material deliberately kept (558, ADR-0004).
- [x] D14: library id handed off from the IO thread; survives recreation (558). `configChanges` left unchanged.
- [x] Undo keeps the workspace/panel; layer selection keeps zoom (558).
- [ ] Device: run `scripts/run-device-tests.sh` (new cases `new_project_resets_per_style_memory_d19`, `undo_keeps_workspace_and_layer_keeps_zoom`); dark-mode toggle during a first save.
- [ ] Remaining extraction: a full `EditorState` object (settings + palettes + per-style memory) with one mutation API; render scheduling and cut-ready coordination out of `StudioActivity`. Reads of the derived style fields can then move to `styleSelection()`.

## M4: Artwork quality and colour (branch m4-559; host-verified, device unverified)

Report: `docs/assessments/2026-11-23-m4-artwork-quality-report.md`. Decisions: ADR-0005, ADR-0006 (proposed).

- [x] Artwork benchmark on real CC0/public-domain photos (`tests/M4ArtBench.java`, `tests/m4/photos`) and mandatory gate `run-v3-m4-quality-gate.sh`.
- [x] D12 photo palette: whole-image OKLab optimizer; duplicate-tolerant surface arrangement.
- [x] Ten new colour schemes (appended; legacy IDs frozen).
- [x] Gap contract: suite generation designs to the export weed gap.
- [x] Shared edges: Torn Collage / Marquetry underlap (base hairlines −88–95%).
- [x] Cut prep discloses filled holes / removed pieces (status, ZIP).
- [x] Linocut tone-to-line mapping (spacing follows tone).
- [x] D8: physical slider floors for curve ribbon, stipple dots, layered cells.
- [ ] D18 Reverse: decide ADR-0006 (recommended: rename now, stack-reversal after a physical test).
- [ ] Lines highlights: dash-length error diffusion along lines (existing retention options measured worse).
- [ ] Linocut: polarity inversion in shadows (coverage tops out near 45%); halo near dark strokes.
- [ ] Confetti likeness: base in median tone, tone-dependent piece density (review finding).
- [ ] Crochet hole loss (~50% at 6–10.5 in): engine-level redesign; slit-only fix measured and rejected.
- [ ] Picker families (UX review proposal), palette list sections, per-style Adjust labels, strings to resources.
- [ ] Device: cut-ready status length/tap, slider notes, render latency on the S22 Ultra.

## M5: Artwork engines, colour, cut constraints (561 = 3.5.0; host-verified, device unverified)

Report: `docs/assessments/2026-11-25-m5-completion-report.md`. Beta: 560 (`docs/assessments/2026-11-24-m5-beta1-report.md`).

- [x] Prismatic: six styles; Diagonal conditioned (560) and size-aware pitch above 6 in (561); conditioning allocation −28–43% at 11 in (local micro-fragment removal).
- [x] Lines: dash-length error diffusion researched and measured on every line style; rejected there (likeness fell 63/63), adopted for Banknote Engraving at 0.8 mm.
- [x] Linocut: relief polarity with Boolean-carved gouges, halo and seam fixes, feature marks; 0 cut findings 3–11 in.
- [x] Confetti: perceptual solid base and photo active-swatch subset (dE −28%). Faces still only suggested at ~390 pieces.
- [x] Crochet: filet redesign; cut findings and hole loss 0 at 3–11 in.
- [x] Colour: OKLab already used for palette association; OKLab stencil k-means measured, kept CIELAB (saved-project geometry). ADR-0006 accepted (option A).
- [x] Contour smoothing audit; Camo de-staircasing implemented, shipped off (base slivers not proven absent).
- [x] Mat fit on every Cricut path incl. the single-upload SVG; compact above/below registration layout (11 in landscape on 12 × 12).
- [x] Deterministic work budgets replace wall-clock geometry budgets.
- [x] Lifecycle: tonal palette snapshot, cut-stage queue starvation, specialty ZIP cleanup; all-style regression matrix.
- [x] CI repaired (JTS jar); M5 benchmark (`M5Bench`, `M5Fixtures`).
- [ ] Device: S22 Ultra and SM-T510 render time and peak heap at 11 in (Prismatic ~390–510 MB host peak), instrumentation suite, PNG/PDF export, mark-layout UI, picker and slider behaviour.
- [ ] Design Space / Silhouette Studio import of single-upload and separate sheets on both mats, including the 291.4 mm compact sheet.
- [ ] Physical cuts: Linocut gouges, Crochet 0.94 mm posts / 1.9 mm meshes, Banknote dashes (~1000 pieces), compact marks 1.5 mm from the sheet edge, 9-sheet Crochet registration.
- [ ] Decisions: non-Cricut cutter size limits; Camo smoothing (shared visible-sheet smoothing); filling Prismatic 3 in micro weed holes; Linocut 3 in reads close to a stencil.

## M6: Architecture and reliability completion

562 is a focused candidate; see `docs/engineering/M6-562.md` for implemented work, pending items and verification limits.

- Extract `EditorState` (settings + palettes + per-style memory) and render scheduling out of `StudioActivity`.
- Declared render inputs per style (cache-key contract); per-style contract (inputs, cut route, smoothing, palette family).
- Lifecycle deferrals from `docs/engineering/M5-LIFECYCLE-AUDIT.md`: A5 palette suggestion dropped on generation bump, A7 `configChanges` for uiMode/fontScale/locale, A8 `onTrimMemory`.
- Shared cut prep: opening radius from the physical minimum (raster grid can erase minimum-width strokes; see M5-LINOCUT.md).
- Picker regrouping (proposal in the lifecycle audit), strings to resources, `SharedExportFiles` to carry `NOT-CUT-QUALIFIED.txt`.
- Low-memory fallback (`ParallelGeometry.forceSequential`) if device peak heap requires it.
- Legacy source-text gates outside the manifest that assert old metadata (rc149, rc166, v2544x): replace with behavioural tests or retire with reasons.

## M7: Production qualification

- Release-variant APK on the device (needs the published privacy URL, `VINYL_PRIVACY_URL`). R8 mapping retained. Play bundle.
- Physical cut matrix (sizes × styles × materials) recorded in `qualification/`.
- Only then call a build production-ready.

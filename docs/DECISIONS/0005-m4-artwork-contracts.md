# ADR-0005: M4 artwork contracts (gap, shared edges, cut-prep disclosure, physical floors, photo palette)

Status: accepted (branch m4-559, build 559 candidate). Date: 2026-11-23.
Evidence: `tests/run-v3-m4-quality-gate.sh` and `docs/assessments/2026-11-23-m4-artwork-quality-report.md`.
All evidence is host-JVM only. Nothing here has been run on the phone, in Design Space or on a cutter.

## 1. One weed gap per suite artwork

- `VinylStyleSuite.generationGapMm(bridgeMm, minimumMm)` is the only derivation of the suite weed gap.
- Generation (`Config.gapMm`, set in `StudioActivity`), `prepareForCutting` and the export inspection all use it.
- Before: generation always used 0.5 mm; the stage and export used the material bridge.
- Default projects (bridge 0.5 mm) produce identical geometry. Engines that ignore `gapMm` (Torn Collage, Crochet, Confetti) are unaffected.

## 2. Shared edges in stacked label sheets: underlap, not joint repair

- Torn-Paper Collage and Organic Marquetry stack label sheets on a full base.
- Each label sheet extends up to (minimum width + weed gap) under the sheets **above** it, and never into lower ones.
- The upper sheet hides the band. Independent per-sheet repair can then move an edge without exposing the base.
- Alternatives considered:
  - **Joint multi-sheet topology repair** (a shared label raster, repaired once, traced per label). It is more general but requires rewriting `CutGeometryConditioner` for label fields and re-validating every organic style. Deferred.
  - **Skip repair for inlay sheets and flag them at export.** Rejected: it leaves sub-minimum geometry to the user.
- Consequences:
  - Lower sheets use more vinyl, hidden under upper ones.
  - Small label pieces that `removeTiny` used to discard now survive attached to their band. This is a visible change, towards the photo's own colour.
  - Option `ExpandedPortraitStylesEngine.Options.sharedEdgeUnderlap` (default true) exists for the visibility test.

## 3. Cut prep discloses what it removes

- `CutRepairSummary` (net per-sheet pieces removed and holes filled) travels with `SuiteCutStage`.
- It is shown in the cut-ready status (problem class: 12 s, tap opens the cut panel) and exported as `cut-prep-changes.csv` plus a README note.
- The conditioner's thresholds are unchanged:
  - drop vinyl under 1.3× minimum;
  - fill waste under 1.3× gap.
  These were not relaxed, because they encode weeding practicality that only a physical cut can re-qualify.

## 4. Physical floors for sliders (D8)

- `PhysicalSliderFloor` supplies a feature floor (the cut minimum) and a gap floor (the export gap).
- The floor is applied to both the slider range and the render snapshot. The displayed value is the value that takes effect.
- Stored values are kept, so lowering the material minimum restores the user's setting.
- Applied to: continuous-curve ribbon width, feature and gap; stipple minimum dot; Layered Cell minimum feature (its render floor is minimum + 0.05 mm).
- The Custom profile's "Minimum cut width" still defines the minimum.

## 5. Photo palette (D12)

- `PhotoPaletteOptimizer` computes photo-derived swatches:
  - a whole-image OKLab histogram;
  - a support floor, so no swatch comes from stray pixels;
  - √support·D² seeding, so distinct accents survive;
  - near-duplicate merge or split;
  - the reported colour snapped to a real member.
- The stencil engine keeps `SourceColorStencilQuantizer`, because changing it changes stencil geometry and goldens. That is a separate decision.
- `ColorWorkspacePalette.selectForSurface` tolerates repeated swatches; it used to throw.

## 6. Colour schemes

- Ten presets are appended (IDs 19–28).
- IDs 0–18 and their colours are frozen (hash-checked). `PaletteRecommendations.CATALOG_SIZE` is derived.

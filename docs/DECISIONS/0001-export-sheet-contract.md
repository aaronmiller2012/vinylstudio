# ADR-0001: ExportSheet contract and single SheetFrame authority

Status: **Accepted, partially implemented** (2026-11-21).
- `SheetFrame` is implemented and used by every cut-sheet writer, Cut Check, the assembly guide and the composite PDF.
- `ExportSheet` is deferred (see "Deferred").

Evidence: `docs/assessments/2026-11-21-export-contract-verification.md`.

## Problem

Four result models (`VinylEngine.Result`, `StyleKit.Result`, stencil raster+cubics, cell/tess pieces) each wrote their own SVG. They chose their own margin and framing convention, and resolved and validated marks separately. That produced confirmed defects:
- **D2:** standard sheets were all black.
- **D3:** a suite style + Snip base was always rejected for a frame mismatch.
- **D4:** with circle crop, the suite, tessellation, cell and stencil exports, the guide and the PDF placed marks outside the art while the preview and the Snip base placed them in the corners.

## Options considered

1. **Patch each writer in place.** Cheapest, but duplication stays: the next writer or call site that calls plain `RegistrationMarks.layout` re-introduces D4.
2. **One frame authority, plus an `ExportSheet` record and a single writer, with an adapter per result model.**
3. **One geometry model for every engine.** This would rewrite about 20 engines. Regression risk is far out of proportion to the defects.

## Decision

Option 2, in two steps. Step one (done) is the frame. Step two (`ExportSheet`) happens when its own motivating defects are worked on.

`SheetFrame.of(artW, artH, registration, markMm, clearanceMm, customMarks, requestedMargin, circleCrop)`:
- resolves margin and mark centers **only** through `RegistrationMarks.artworkLayout`, the function the preview and the standard engine already used;
- provides the canonical SVG root: `viewBox="0 0 W H"` + `translate(margin)` group carrying the sheet's vinyl color.

**Why this framing convention** (and not the shifted viewBox origin the earlier draft proposed):
- the standard, stencil, cell and tessellation writers already used it; only `NewStyleExport` differed;
- adopting it changes one writer, not four, and keeps the default style's files byte-identical apart from fill color;
- it avoids negative viewBox origins, whose handling by cutter importers is less certain than a plain `translate`.

**Why not a stricter writer check instead:**
- `CutSheetZipWriter` could have compared *effective* mark positions instead of root frames, which would have accepted D3;
- but that would hide mismatches, not prevent them;
- one convention keeps the cheap, fail-closed root-frame check meaningful.

## Deferred: `ExportSheet`

It was not introduced now because:
- every confirmed export defect was fixed by the frame plus the per-writer fill;
- the writers emit genuinely different geometry (circles + compound polygons, loops + strokes, pixel pieces, raster-traced cubics);
- a shared record would require a common geometry model, which means changing four writers' output with no defect demanding it;
- an `ExportSheet` that nothing consumed would be abstraction for its own sake.

**Revisit when** fixing the manifest/L-number drift, the stale layer names and the combined-file stacking order (assessment §D, S3; exporter finding 3). Those need one source for order, label, file name and color, which is exactly what `ExportSheet` provides.

## Consequences

- One function decides where marks go. The preview, Cut Check, every writer, the Snip base, the guide and the PDF agree.
- Rectangular-crop exports keep identical physical geometry. This was verified by parsing 64 sheets from 556 and from the new code.
- SVG text changes:
  - standard sheets now carry their swatch color, and so do their marks;
  - marked suite sheets use the translate convention.
  - Geometry signatures are unchanged; golden SVG hashes are informational only.
- Circle-crop specialty exports now use corner marks. If a style's geometry reaches a corner, Cut Check reports the collision instead of silently moving the marks (0 of 70 synthetic renders collided).
- Specialty writers still bake the margin into coordinates instead of using the translate group. Their root frame is identical, so this is fine. Migrating them to `SheetFrame.open` is optional cleanup, not required.

## Guard

`tests/V3ExportSheetContractTest.java` (in the mandatory gate `run-v3-export-sheet-contract-gate.sh`) drives real engines through `CutSheetZipWriter` and checks:
- colors;
- frame acceptance;
- cross-sheet mark equality;
- preview mark equality, for every exporter family.

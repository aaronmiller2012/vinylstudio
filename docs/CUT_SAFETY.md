# Cut safety (as implemented, build 556)

## Physical assumptions

- **Cutter:** Cricut Explore Air 2. The usable area of a 12×12 mat is about **11.5×11.5 in (292.1 mm)**.
  - 12×24 mats are supported deliberately: the 11 × 14 and 12 × 16 presets need them, and the Size dialog makes the user explicitly keep a size that won't fit 12×12.
  - Sheets above 11.5×23.5 in are rejected.
  - Every Cricut cut ZIP states the required mat in `CRICUT-MAT.txt` (`CricutMatPreflight.requiredMat`).
  - An 11 in-wide marked sheet (8 mm margins, 295.4 mm) is wider than every Cricut mat's 11.5 in short side and is rejected. Circle-crop compaction can avoid this.
- **Material minimums** come from `CutMaterialProfile.java:12-15`: minimum feature 0.65–1.10 mm depending on the profile. `Settings.minimumMm` defaults to 0.8 mm, and `bridgeMm` to 0.5 mm.
- **Registration margin** differs by export path. Mark *positions* are unified by `SheetFrame`; margins are not:
  - 8 mm for suite styles (`NewStyleExport.java:18`);
  - `outerSpacingMm` (default 8 mm) for standard;
  - 10 mm for document/PDF (`StudioActivity.java:9099`).
  - Consequence: an 11 in sheet + 2×8 mm = 295.4 mm, which does **not** fit a 12×12 mat.

## Measurement

| Check | Class | Method | Notes |
|---|---|---|---|
| Source preflight | `StyleCutGeometryPreflight` | Exact segment-to-segment distance with a mm spatial hash (`:325-414`). Even-odd ray test decides vinyl vs waste (`:456-486`). Convex-hull min width for small loops (`:210-229`) | Real vector measurement. Heuristic exemptions can hide slivers: `sameBoundaryNeighborhood` (`:170-182`), `shortCornerConnector` (`:190-203`), `deadEnd` enclosed area < min² (`:434-455`) |
| Final per-SVG gate | `FinalCutSheetSafety`, then `DenseCutSheetSafety` for dense sheets | Parses the emitted SVG; flattens curves at 0.02 mm | Dense check gives up (UNVERIFIED) if one contour touches > 256 tiles. This is common at 11 in (D17) |
| Self-intersection | `StyleKit.checkSelfIntersections` / `selfIntersections` | Exact segment test on a uniform grid whose cell grows to stay ≤4M cells; total work bounded | An exhausted budget or non-finite input is **indeterminate**, which `selfIntersections` reports as a crossing (fails closed). It used to return "simple" (D11, fixed) |
| Footprint | `CutIntelligence` | Area-only bound | Advisory |
| Tonal raster | `VinylCutPreflight` | Axis-aligned run lengths | Under-reads diagonal narrowness by up to √2. Feeds `cut-checks.csv` only |

## Failure policy

- Hard reject: geometry outside the artboard, frame/artboard mismatch between sheets, NaN or huge coordinates, element cap, mat fit.
- Otherwise the export is blocked until the user explicitly approves review. The ZIP then contains `NOT-CUT-QUALIFIED.txt`.
- Geometry is never silently repaired to pass the gate. It is changed only by the generation-time drops and the export-time `prepareForCutting` listed in `RENDER_PIPELINE.md`.

## M4 changes (ADR-0005)

- Suite generation uses the export weed gap (`VinylStyleSuite.generationGapMm`), not a fixed 0.5 mm.
- Torn Collage / Marquetry label sheets underlap the sheets above them, so per-sheet repair no longer exposes the base along shared edges.
- Cut prep states the holes it filled and pieces it removed (status line, `cut-prep-changes.csv`). The thresholds (1.3× minimum / gap) are unchanged.
- `PhysicalSliderFloor`: curve-ribbon, stipple-dot and layered-cell sliders start at the material minimum; the render applies the same floor.

## Known gaps

- Slider ranges permit features below the profile minimum (D8): fixed for curve ribbon, stipple and layered cells; suite "Line weight" relies on per-engine clamps.
- Specialty raster ≈ 0.32 mm/px at 11 in (D13).
- Snip is implemented three different ways (shape drop, pixel mask, exact JTS).
- Area-only micro-fragment removal can delete thin but long features.
- TSP ribbon repair narrows to 0.02 mm (`TspArtEngine.java:119`).

## Physical validation status

**No physical cut test has been recorded for build 556.** Record cutter tests in `qualification/`. Never claim cutter qualification without an entry there.

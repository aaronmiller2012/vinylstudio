# Render pipeline (as implemented; branch export-sheet-contract, after M2)

This describes the real code paths, not a target design. For the target, see `docs/DECISIONS/0001-export-sheet-contract.md`.

## 1. Scheduling

- `StudioActivity.requestRender()` bumps `serial` (volatile), cancels the STANDARD and PREVIEW lanes, and debounces (`RenderDraftPolicy.DEBOUNCE_MS` while dragging, otherwise 100 ms).
- Specialty styles (`alternateActive()`, `StudioActivity.java:790`) go through `requestAlternate()` on the SPECIALTY lane, keyed by `alternateGeneration`.
- Every worker receives `settings.copy()` (a deep copy) plus final locals. Results are posted with `postUi` and dropped unless the generation still matches.
- `StudioRenderController` runs single-thread lanes with bounded queues; cancel = `Future.cancel(true)`. `CancellationCheckpoint.check()` is polled in engine loops (every 64 samples).

## 2. Style dispatch

There is **no registry**. The style in effect is decided by flags, checked in this order:
`newStyleId>=0` (suite, `VinylStyleSuite.generate` if/switch, about 20 engines) · `continuousStyle` · `tonalStencilMode` ·
`mazePortraitMode` · `tessellationMode` · `layeredCellMode` · otherwise `VinylEngine.generate` keyed on `settings.mode`
(CMYK / SPOT / STENCIL) and `settings.pattern`.

## 3. Source preparation

- **Standard:** `ImageLevels.apply` (exposure, contrast, levels; luma delta in gamma space). Draft renders downsample first (`RenderDraftPolicy.dimensions`, `draftSourceCache`).
- **Specialty:** `specialtyPrepared` caps the final source at **850 px** (draft 480). `SpecialtyCutSafety.prepare` turns Snip into alpha and builds the `allowed` mask.
  - D13 evaluation: raising the cap to 1200 or 1600 px left suite geometry essentially unchanged (contours and vertices within about 1%), because the engines build geometry on their own millimetre grids (Detail, minimum cut). The cap is kept.
  - Open: whether the Snip/circle mask edge (quantized at the source pitch) needs a finer mask at large sizes; that needs real photos on a device.
- **Tone separation reference:** `Settings.separationBackground` (ADR-0003). `Settings.paper` (mounting surface) is display-only.

## 4. Geometry generation (four result models)

| Model | Producers | Units | Snip handling |
|---|---|---|---|
| `VinylEngine.Result` (shapes: circles and compound polygons per layer) | `VinylEngine`, `LineArtEngine`, `StencilSeparationEngine` via `LayerParallelism` | mm | `add()` drops whole shapes that cross the Snip (`VinylEngine.java:384-404`) |
| `StyleKit.Result` (loops per layer) | `VinylStyleSuite` engines | mm | pixel-center `allowed` mask. Exact JTS intersection only in Prismatic and Trio/Confetti (`VectorBooleanEngine`) |
| Stencil raster + cubic paths | `MultilayerStencilEngine`, `SharedStencilBoundaries`, `SmoothStencilContours` | px → mm at write | raster |
| Cell / tessellation pieces | `LayeredCellPortraitEngine`, `TessellationEngine` | px | raster mask |

Geometry is modified (not just measured) in these places:
- `VinylEngine.add` (tiny pieces).
- JTS `removeMicroFragments` (area only, `JtsVectorBooleanBackend.java:84-99`).
- `PatternPortraitTrioEngine.java:176-178` and `CutGeometryConditioner.java:174-176` (loops dropped by area or self-crossing).
- `prepareForCutting` at export.

### M4 additions

- Suite `Config.gapMm` = `VinylStyleSuite.generationGapMm(bridge, minimum)`, the same gap `prepareForCutting` uses.
- Torn Collage / Marquetry: `ExpandedPortraitStylesEngine.underlap` extends each label mask under higher labels before tracing.
- `SuiteCutStage.repair` (`CutRepairSummary`) is computed with every cut-ready stage.
- Photo palettes: `PhotoPaletteOptimizer` (Prepare photo palette, stipple photo palette, layered-cell suggestion).

## 5. Preview

- Standard: `CanvasGeometry`.
- Suite: `NewStyleVectorCache` (even-odd).
- Registration marks in the preview come from `automaticPositions(…, circleCrop)` (`CanvasView.displayedMarks`). Every exporter resolves the same positions through `SheetFrame` (ADR-0001). `tests/V3ExportSheetContractTest.java` asserts the equality.

## 5b. Cut-ready stage (ADR-0002)

After every FINAL render, a background stage on the cut-preparation executor produces the exact export geometry and replaces the preview with it:
- **Suite styles:** `SuiteCutStage.prepare` → `VinylStyleSuite.prepareForCutting` (narrow-feature repair). It changes geometry in about half of style/size cases.
- **Stipple:** `StudioActivity.fullResolutionStipple` (≤1200 px, full relaxation). The 350 px draft preview has 4–7× fewer dots.
- **Cancellation:** cancelled by any newer render; publication is gated by render generation and result identity.
- **Status:** "Preparing cut-ready …" → "Cut-ready: the preview shows the exact geometry that will be cut."

## 6. Export

1. Suite styles export `SuiteCutStage.reusable(...)`, the cut-ready preview. If it is not ready, or was prepared with other cut parameters, export prepares once and publishes that result to the preview before any review dialog.
2. Stipple exports the cut-ready result, or computes `fullResolutionStipple` once and publishes it.
3. Source preflight (`StandardCutSafety` / `StyleCutGeometryPreflight`), then the `CutSafetyReviewPolicy` dialog.
4. Per-sheet SVG writers. All share one root frame (`viewBox 0 0 W H`; content at +margin) and one mark resolution (`SheetFrame`):
   - `VinylEngine.svg` and the Snip base: `SheetFrame.open` (translate group), filled with the layer swatch.
   - `NewStyleExport` (suite): `SheetFrame.open`, 8 mm margin (`SheetFrame.SPECIALTY_PADDING_MM`) when marked; unmarked sheets have artboard = artwork.
   - Stencil, cell and tessellation writers: margin baked into the coordinates, frame from `SheetFrame.of`.
   - Composite PDF/PNG (presentation only): 10 mm margin, mark positions from the suite frame.
5. `CutSheetZipWriter.put`: `FinalCutSheetSafety` (or `DenseCutSheetSafety`), artboard/frame parity across sheets (0.05 mm), `CutSvgCompatibility` (Cricut 5000 elements), `CricutMatPreflight` (reject above 11.5×23.5 in). `finish()` writes `CRICUT-MAT.txt` stating whether a 12×12 or 12×24 mat is required.
6. `CombinedLayerSvg`: concatenates the sheets in `put()` order into `00-ALL-LAYERS-one-upload.svg` (skipped above the element cap).
7. `finish()`: blocks, or writes `NOT-CUT-QUALIFIED.txt` after explicit review. Then `StagedCutZip` (staged, CRC read-back, byte-verify after SAF copy).

## Determinism

Output is deterministic for photo + settings + engine build (561). The former wall-clock budgets are now work budgets, so a faster device cannot produce different artwork:
- TSP 2-opt: at most `timeBudgetMs × TspArtEngine.WORK_PER_MS` candidate evaluations (2500 × 20,000).
- `prepareForCutting`: `PREPARE_BUDGET_WORK` = 2.5 M vertices (+8 per loop); layers past the budget stay unconditioned identically on every device.
- Cancellation (`CancellationCheckpoint`) still stops work for responsiveness; a cancelled result is discarded, never exported.
- Analysis-only clocks remain in `FootprintClearance` (750 ms) and `SpecialtyArtworkMetrics` (5 s). They return "incomplete"/"unavailable" text and never change geometry.
- `M5DeterministicGeometryTest` checks identical geometry hashes across repeats, sequential vs parallel, an artificially slowed backend and CPU-hog threads.

## Style contract status

There is no written per-style contract yet. The facts each style should eventually declare are:
- render inputs (which Settings fields it reads);
- the layer count range and physical stacking order;
- the Snip method;
- whether conditioning happens at export;
- its minimum-feature behavior.

Collect these in a `docs/STYLE_CONTRACTS.md` table as styles are migrated to the ExportSheet contract (ADR-0001).

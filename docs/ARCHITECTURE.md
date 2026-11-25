# VinylStudio architecture (build 556; corrected 2026-11-21)

VinylStudio is a single-activity Android app (minSdk 26, targetSdk 36, Java 17, no
Kotlin, no AndroidX UI libraries). It turns a photo into layered, physically cuttable
vinyl artwork and exports per-color SVG/DXF cut sheets, PNG/PDF artwork and an assembly guide.
All ~293 classes live in one package, `com.halftonestudio`.

Related: `docs/RENDER_PIPELINE.md`, `docs/CUT_SAFETY.md`, `docs/QUALITY_GATES.md`, `docs/ROADMAP.md`,
`docs/DECISIONS/`, and the full assessment in `docs/assessments/2026-11-21-engineering-assessment.md`.

## Flow

```
Import (photo picker / Share / Open with)
  -> Crop (PhotoCropEditor) -> Snip outline (Snip*) -> Style + layers + colors
  -> Render (StudioRenderController lanes; *Engine classes)
  -> Cut preflight (StyleCutGeometryPreflight, *CutSafety, VinylCutPreflight)
  -> Verified cut ZIP (StagedCutZip, CutSheetZipWriter, SvgDxfR13Exporter)
  -> Save via Android picker, optional Share sheets to the cutter app (SharedExportProvider)
```

## Layers

| Layer | Main classes | Notes |
|---|---|---|
| UI shell | `StudioActivity` (~12.8k lines), `PhotoCropEditor`, `VinylQuickStartGuide`, `VinylColorEditor`, `StudioStylePreview` | All views built in code; no layout XML. Owns editor state, workflow stages and dialogs. |
| Threading | `StudioRenderController` (PREVIEW / STANDARD / SPECIALTY render lanes, disk I/O, cut preparation, diagnostics), `StudioLibraryJobs`, `PhotoPersistenceQueue`, `LatestRequestWorker`, `CancellationCheckpoint` | Single-thread lanes with bounded queues. Results are posted to the UI only if their generation/serial still matches. |
| Style engines | `VinylEngine` (CMYK/spot halftones), `LineArtEngine`, `MultilayerStencilEngine`, `TessellationEngine`, `LayeredCellPortraitEngine`, `StyleKit`/`VinylStyleSuite` (33 selectable styles) and about 30 more `*Engine` classes | Pure computation (only 2 of 36 engine files import `android.*`). Deterministic for photo + settings + engine build **except** deterministic work budgets (TSP 2-opt and `prepareForCutting`; wall-clock budgets were removed in 561). Dispatch is an if/switch in `VinylStyleSuite.generate` plus StudioActivity's style fields, which are derived from one `StyleSelection` value through a single write path, `applyStyleSelection` (558, ADR-0004; no registry). The picker shows about 101 tiles. |
| Vector geometry | `VectorBooleanEngine` + `JtsVectorBooleanBackend` (JTS 1.20), `CutGeometryConditioner`, `SmoothStencilContours`, `OrganicFlowCutSmoother`, `RegistrationCutGeometry` | JTS unions (1/1024 mm grid) are used only by Prismatic and Trio/Confetti. Other styles are not unioned. Snip is implemented three ways (shape drop in VinylEngine, pixel mask for suite styles, exact JTS in those two engines). |
| Cut safety | `StyleCutGeometryPreflight`, `StandardCutSafety`, `SpecialtyCutSafety`, `FinalCutSheetSafety`, `DenseCutSheetSafety`, `CutIntelligence` | Minimum cut width / weed gap checks against the selected material; unverified sheets require explicit review. |
| Export | `SvgDxfR13Exporter`, `CutSheetZipWriter`, `StagedCutZip`, `ArtworkDocumentExport`, `AssemblyGuideDocument`, `CricutMatPreflight` | Physical millimetre artboards; the ZIP is staged and verified before the save dialog opens. |
| Persistence | `StudioProjectLibrary` (immutable revision bundles), `ProjectArchiveReader`, `StudioProjectArchiveWriter`, `StudioProjectBackup`, `PhotoRevisionStore`/`ProjectPhotoStore` (autosave), `ProjectSettingsSchema`/`Migration` | See *Data* below. |
| Platform shims | `PlatformCompat`, `DocumentAccess`, `SharedImageIntent`, `SharedExportProvider` | All deprecated-API use is confined to `PlatformCompat`. |

## Data

- **Project library**: `files/project-library/<id>.rev-<time>-<uuid>.bundle/` holding `project.zip`
  (photo PNG, original PNG, `settings.json`, optional native source), `preview.jpg` and `card.properties`
  (name, style, SHA-256/size/mtime fingerprint). A save stages a directory and publishes it with one rename;
  the two newest valid revisions are kept. The gallery lists from the fingerprint and opening fully verifies.
- **Autosave**: `files/vinyl-photo-revisions/` photo pairs committed atomically, plus editor JSON in
  `SharedPreferences`. A `PhotoPairToken` links the two, so a process kill between them is detected.
- **Settings JSON** carries `schema` (migrated by `ProjectSettingsSchema`) and `engineBuild`. Artwork is *not*
  stored; it is regenerated on open, and an older `engineBuild` triggers a one-time review notice.
- **Backup**: cloud backup is disabled. Android 12+ device-to-device transfer copies the library, palettes,
  calibrations and style favourites (`res/xml/data_extraction_rules.xml`), and users can export or restore a library ZIP.

## Tests and gates

- `app/src/test`: JUnit tests run by Gradle.
- `tests/`: about 800 host gates (Java tests compiled with `javac` against small `android.graphics` stubs, plus Python
  source and integration checks). **`tests/gates.manifest` is the mandatory list**, kept in sync with CI, NAS,
  Play and Gradle by `scripts/sync-gates.py` and enforced by `tests/check-gate-manifest.py`.
- `app/src/androidTest/StageOneInstrumentation`: dependency-free on-device suite, run with
  `scripts/run-device-tests.sh`.
- Physical evidence: `qualification/` with `tests/check-qualification-status.py`.

## Corrections to earlier versions of this file (verified 2026-11-21)

- There are **four result models**, each with its own SVG writer: `VinylEngine.Result`, `StyleKit.Result`, stencil raster+cubics, and cell/tess pieces.
  Since the export-sheet-contract change, they share **one frame authority**, `SheetFrame` (ADR-0001).
  It resolves margin and mark centers through `RegistrationMarks.artworkLayout`, including circle crop, for every writer, the Snip base, Cut Check, the assembly guide, the composite PDF and the preview.
  It also defines the common SVG root (`viewBox 0 0 W H` + `translate(margin)`).
- Suite `prepareForCutting` and the full-resolution stipple now run in a background **cut-ready stage** after each final render, and export reuses that result (ADR-0002). Before that change, they ran only at export.
- Tone separation uses `Settings.separationBackground`; `Settings.paper` (mounting surface) is display-only (ADR-0003).
- `StyleKit.checkSelfIntersections` returns valid / crossings / indeterminate. Indeterminate fails closed.
- Canvas status messages follow `StatusLinePolicy`. User-facing failure text goes through `FailureText`.
- `VinylEngine.svg` writes each layer's swatch as its fill (it wrote `#000000` up to build 556).
- The style in effect is decided by `newStyleId`, `continuousStyle`, `tonalStencilMode`, `mazePortraitMode`, `tessellationMode`, `layeredCellMode` and `settings.mode`. No single place enforces exclusivity.

## Known structural debt

1. `StudioActivity` (12,575 lines; gallery and UI factory extracted in 554) combines UI construction, editor state, workflow and I/O orchestration. The next extraction
   candidates, which have the fewest cross-references, are the project gallery dialog, the export flow
   (`publishPrepared*`, `offerShareCutSheets`) and the photo import and restore flow. Many inherited source-text
   gates assert literal code inside `StudioActivity`, so each extraction must update those gates in the same change.
2. Many gates assert source text rather than behaviour. Prefer executable tests for new work.
3. UI text is mostly hard-coded English (see `res/values/strings.xml` for the migrated portion).

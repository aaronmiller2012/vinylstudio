# ADR-0002: A cut-ready render stage shared by preview and export

Status: **Accepted, implemented** (2026-11-21), for suite (art) styles and tessellation stipple.

## Problem (D7)

Two families exported geometry the user never saw:

- **Suite styles.** `VinylStyleSuite.prepareForCutting` (narrow-feature repair: re-raster and re-trace of flagged regions) ran only inside the export. On the host it changed geometry in **34 of 66** style/size cases.
- **Stipple.** The preview always used a 350 px, draft-quality relaxation. Export regenerated at up to 1200 px at full quality. The preview showed about 1.2–1.9k dots while the export cut **6.4–13.6k**.

## Options considered

1. **Condition in every final render.** Simplest, but conditioning costs about 6.7× the generation time in total (host: up to 7.8 s for one style at 10.5 in; stipple export 0.5–4.8 s). Every slider release would feel slow on the phone.
2. **Condition only when entering Export.** Cheaper, but the Adjust/Colors preview would still not show the cut, and Cut Check measurements would describe different geometry from the export.
3. **A cancellable background "cut-ready" stage after each final render, whose result replaces the preview and is reused by export.** (Chosen.)

## Decision

- **Draft → final → cut-ready.** The draft (during slider drag) and the final render stay as they were. After a final render publishes, the stage runs on the existing single-thread cut-preparation executor:
  - Suite: `SuiteCutStage.prepare` records the minimum cut, bridge and style it used.
  - Stipple: `fullResolutionStipple`, the export's former inline code, moved verbatim.
- **Publication.** On completion, and only if the render generation is unchanged and the shown result is still the one it was derived from, the preview, Cut Check counts and export source switch to the stage result. The status reads "Preparing cut-ready …" and then "Cut-ready: the preview shows the exact geometry that will be cut."
- **Export.** Export reuses the stage result when it matches (`SuiteCutStage.reusable`, `stippleCutReady`). Otherwise it runs the same code once and publishes the result to the preview before any review dialog, so the reviewed and exported geometry are the result the user sees.
- **Cancellation.** A newer render cancels the stage (`Future.cancel(true)`; engines poll `CancellationCheckpoint`). Publication is gated by generation and result identity.

## Consequences

- Preview, measurements and export agree once the preview is cut-ready. Parity is by construction (the same object). `V3PreviewExportParityTest` checks it for 8 styles × 2 sizes.
- **CPU cost.** The background stage spends CPU after each final render, the same work the export used to do once. Slider drags cancel it. Device battery and latency are unmeasured.
- **Deadline.** (561: replaced by the deterministic `PREPARE_BUDGET_WORK` vertex budget; the text below describes the earlier wall-clock behaviour.) The suite stage keeps `prepareForCutting`'s 45 s deadline. On a slow device, remaining layers may stay unconditioned. Export reuses that same result, so parity holds, but output can depend on device speed.
- **Stipple path.** It needs Android `Bitmap`, so it is not host-testable. It is covered by construction (shared method, reused object) and needs a device check.

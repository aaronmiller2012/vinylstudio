# ADR-0004: StyleSelection and New Project reset

Status: accepted (build 558). Date: 2026-11-23.

## Context

`StudioActivity` kept the artwork style as ten independent fields (`continuousStyle`,
`tonalStencilMode`, `mazePortraitMode`, `tessellationMode`, `layeredCellMode`, `offsetCellMode`,
`bottleCapMode`, `fishScaleMode`, `overprintCellKind`, `newStyleId`). Each picker cleared a
different subset; the Shapes/Lines pickers cleared them before the undo checkpoint, so undo
could not return to the previous style. New Project reset only some per-style state (D19).

## Decision

1. `StyleSelection` is an immutable value: one `Kind` plus the line style, cell variant,
   overprint kind or suite id that kind needs. `StyleSelection.fromLegacyFlags` holds the
   load precedence (suite > line > tessellation > layered > tonal > maze > halftone; overprint >
   fish > bottle > offset).
2. `StudioActivity.applyStyleSelection` is the only writer of the ten fields
   (`tests/check-m3-editor-state.py`). The single exception is the loader's early
   `newStyleId=restoredStyleId(...)`, needed by per-suite restore; the selection applied at the
   end of `restoreArtworkModes` is authoritative. Pickers apply their selection after `checkpoint()`.
3. The ~600 reads of those fields are unchanged in this step; they can migrate to
   `styleSelection()` incrementally.
4. New Project restores a pristine editor snapshot captured in `onCreate` before any autosave
   loads, through the same path undo uses. **The cutter (output device) and the vinyl material
   profile are kept**: they describe the user's equipment, and the material sets the minimum
   cut width, so resetting it would silently allow thinner features than the vinyl supports.

## Consequences

- Contradictory style combinations cannot be stored or saved.
- `V3M3StyleSelectionTest` checks the new precedence against the 557 code for all 3,456 saved-flag
  combinations, so existing projects open in the same style.
- Per-project Confetti spacing/organic memory now resets to defaults when a project has no saved
  entry (previously the previous project's values remained).

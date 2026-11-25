# ADR-0003: Separation background is independent of the mounting surface

Status: **Accepted, implemented** (2026-11-21).

## Problem (D5)

`Settings.paper`, the mounting surface the user picks in Colors, was also the reference color for tone separation:
- transparent pixels were flattened onto it;
- spot-color ownership competed against it;
- CMYK white balance, stencil quantization, line contour bending and stipple substrate skipping used it.

Changing the surface did not re-render, so the shown geometry went stale. The next unrelated edit then silently added or removed cuts. In the red run, 10 of 16 surface comparisons changed cut geometry.

## Options considered

1. **Re-render on surface change.** Keeps the coupling. A display choice would still change what gets cut, contrary to the product requirement.
2. **Remove the background reference from the engines.** This changes the tone algorithms and every golden. A reference color is genuinely needed, for example for transparent pixels.
3. **An explicit, separately managed `separationBackground`.** (Chosen.)

## Decision

- `Settings.separationBackground` (default `VinylEngine.DEFAULT_SURFACE`, the old paper default) is the only color that tone separation treats as "no vinyl".
  - It is read by `VinylEngine`, `LineArtEngine`, `StencilSeparationEngine` and `OpaqueCmykLineBudget`.
  - It is fed into the suite, stipple and spiral configs built in `StudioActivity`.
- `paper` is display-only: the preview background, guide and PDF composite, and the production summary text.
- **Migration.** Projects saved without `separationBackground` inherit their saved `paper`, so their geometry reproduces exactly on reopen. New projects use the default. No schema bump: the key is additive and ignored by older builds.
- There is no UI for the separation background yet. It is a per-project value. If users need "omit vinyl that matches my board", it should become an explicit control, not a side effect of the surface.

## Consequences

- Changing the mounting surface never changes cut geometry, layer colors, order, enabled layers or size (`V3MountingSurfaceGeometryTest`). `check-m2-render-inputs.py` guards the activity wiring.
- Goldens are unchanged, because the default reference equals the old default paper.
- **Behavior change.** For new projects on a dark board, a vinyl color close to the board is no longer automatically starved of pixels. This is now an explicit future control, not an accident.

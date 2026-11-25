# ADR-0006: Reverse palette semantics (D18)

Status: **accepted, option A** (M5, 2026-11-24). Originally proposed 2026-11-23. No saved-project geometry or key changes.

## Observed behaviour (build 558, unchanged in M4)

"Reverse" is one label for three different operations:

| Context | Implementation | What actually happens |
|---|---|---|
| Classic CMYK | `PaletteOrientation.orientCmyk` (keeps Y first and K last, reverses the interior), applied in `StudioActivity` by writing `settings.layers.get(i).color` | Only vinyl **colours** move. Each `VinylEngine.Layer` keeps its `cmykChannel`. With 4 layers, the cyan separation is cut in magenta vinyl and the magenta separation in cyan. The stack order and the separations are unchanged. |
| Presets, photo and suite styles | `PaletteOrientation.reverseActive` / `orient` after style sorting (`PerceptualLayerColorEngine.finishPreset`, `VinylPaletteMapping`) | The active swatch order is reversed, so tone layers get mirrored colours. Lightest-on-bottom becomes darkest-on-bottom, and the geometry follows the slot. |
| Stained Glass and overprint cells | `orientKeepingLead`, `reverseActive(..., keepLead=true)` | Panes are reversed and the structural lead/foundation stays at L1. |
| Tonal stencil | `PhysicalLayerColorReverse.reverse` | The displayed physical stack colours are reversed onto stable source slots. |

All of them are involutions: toggling twice restores the original. Saved projects store
`paletteReversed` per palette key.

## The three distinct operations

1. **Reverse stacking order.** Move whole sheets, each with its separation and its colour. Occlusion changes and the colours stay correct.
2. **Swap assigned swatches.** This is a creative recolour, and the separations stay where they are. It is the current CMYK behaviour.
3. **Recalculate separations for new colours.** Re-separate the photo against the reversed palette, as spot colours.

## Options

- **A. Keep the behaviour and rename it.** Use "Swap colours" for CMYK (a creative recolour) and "Reverse tones" elsewhere. No saved-project impact.
- **B. Make CMYK Reverse a true stack reversal.** The separation follows its colour. Saved projects with `paletteReversed["cmyk"]=true` would render differently, so a migration notice is needed.
- **C. Offer two explicit actions:** "Reverse stacking" (option B) and "Swap colours" (option A). This is the clearest option but adds a control.

Recommendation: **A now** (a wording-only change, no geometry change, safe for saved projects), then decide **C** with a physical test. Opaque stacking makes "reverse stacking" change the visible image, so it must be seen on vinyl before it becomes a default.

## Decision (M5): option A, wording only

M5 re-tested the invariants on the real palette functions (`tests/M5ColorStateTest.java`, 10 styles x 30 presets x 2..8 sheets x 6 mounting surfaces) and found no defect that would justify changing geometry:

- Every Reverse helper (`reverseActive` with and without lead, `orientCmykActive`, `PhysicalLayerColorReverse`) is an involution; parked swatches never move; the structural lead/L1 never moves.
- Classic CMYK default stack is Yellow L1 ... Black uppermost; Reverse keeps both anchors.
- The mounting surface never changes a photo-derived palette for any style, nor a named palette for any surface-independent family. The surface-aware `ColorWorkspacePalette.arrangedForSurface` runs only on an explicit palette apply for the generic photo-cell family (documented in ADR-0003 consequences).
- Exported file names and labels carry the edited colour hex and the stack index (`ExportColorLabels`).

So: option B (stack reversal) and C (two actions) are **not** adopted now. They would change saved projects with `paletteReversed["cmyk"]=true`, and opaque stacking makes a true stack reversal change what is visible, which needs a physical test first.

### UI strings for the integrator (StudioActivity, `action(paletteActions,"Reverse",...)`, about line 7186)

| Context | Button label | Helper text |
|---|---|---|
| Classic CMYK | `Swap colors` | `Swaps the cyan and magenta vinyl colors. Yellow stays at the bottom and black on top; the separations do not move. Tap again to restore.` |
| Presets, photo and suite styles | `Reverse tones` | `Mirrors the vinyl colors across the sheets in use. The base sheet of styles with a solid base stays in place. Tap again to restore.` |

`quality_reverse_the_current_physical_vinyl_sheet_colors_keep_the_m` (content description) should say "Swap" / "Reverse tones" to match. The saved key stays `paletteReversed`.

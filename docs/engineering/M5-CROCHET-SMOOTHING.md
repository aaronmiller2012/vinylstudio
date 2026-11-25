# M5 (Product 561): Crochet redesign and contour-smoothing audit

All numbers come from the host JVM (`tests/m4/photos`). The settings were:

- 0.8 mm minimum and 0.5 mm bridge, as in M4;
- the 9-sheet suite configuration from `M4ArtBench.suiteConfig`.

No device, Design Space or physical cut was performed.

## A. Crochet (style 39)

### Root causes (560 engine)

All of these are in `NineLayerCraftPatternEngine.crochet()` and `textilePitch()`.

1. **Undersized drawing widths.**
   - Posts are drawn at `yarn*.95`, chain arches at `yarn*.78` and the bottom bar at `yarn*.86`.
   - Here `yarn = max(minCut, ...)`, and with default settings `yarn == minCut`. Every one of those widths is therefore a fraction of the minimum by construction.
   - The capsules are then rasterised on a 0.27–0.32 mm grid. A 0.89 mm capsule can cover only 2 pixel centres, which traces as 0.62 mm.
2. **Narrow slits in filled cells.**
   - A filled cell has 3 slits of about 1.1 mm. Disks of radius 0.56 mm at the post tops narrow them further.
   - Enclosed holes that narrow sit next to sub-minimum posts. The cut stage's opening removes the posts, which merges or fills the holes. That is the "hole loss".
3. **Coarse pitch.**
   - The pitch floor is `minCut*6.6` = 6.18 mm at every size: only 12 columns at 3 in and 45 at 11 in.
   - Fill was decided by `lum<.49` regardless of the foundation colour. With a dark L1, light skin rendered as sparse light yarn over dark L1, so tone was inverted. Measured tone was 0.39 at 3 in.
4. **Cut-stage load.** The cut stage ran 15–58 s per artwork, around the 45 s wall-clock budget. Results depended on host load.

### Redesign (nested `NineLayerCraftPatternEngine.FiletCrochet`)

The design follows real filet crochet:

- a square grid of double-crochet **posts** and chain **bars**;
- each square is an open **mesh** (chain space), a **half** block (one extra post, only where both spaces stay weedable) or a solid **block**.

Geometry is built on the raster surface's own pixel lattice, so traced widths are exact pixel multiples:

- **Posts.** Post = ceil(minCut/px) pixels, at least 0.936 mm for a 0.8 mm minimum.
- **Spaces.** Space ≥ max(1.75·minCut, 3·gap), about 1.9 mm.
- **Pitch.**
  - It is the artistic size/Detail pitch, but never below post+space, and never below post/0.30 so that the yarn stays at most 30% of the pitch.
  - Small artwork therefore gets a coarser grid instead of sub-minimum stitches.
  - Cell count is capped at 6500, so no sheet comes near the 7000-loop limit.
- **Joints.**
  - Each square owns its left post and top bar, each extended by one post width. Same-colour neighbours overlap by a full post × post joint, never by a diagonal pixel corner.
  - Blocks cover the whole extended square.
  - Different-colour neighbours are separated by a whole space.
- **Fillets.** These soften the space corners, at 0.12 of the side. They are drawn only where both members at that corner are on the same sheet, which avoids free 45° spikes.
- **Snip.** A square is stitched only if its whole footprint is inside the Snip, so the Snip never clips a post into a sliver. L1 shows at the ragged edge.
- **Tone.** An open space shows L1, so each (yarn colour, stitch) pair is a physical mix with the foundation in linear RGB, compared in OKLab.
  - Each square takes the closest pair: 50/50 rank-mapped and photographic lightness for L, photo chroma for a and b.
  - Lightness error is diffused in serpentine order, with a small bias toward open stitches.
  - Every sheet is guaranteed at least one square.
- **Compatibility.** Style IDs and saved keys are unchanged. `crochet()` (single stitch) is kept for the RC168 primitive test.

### Experiments (astronaut and horse at 3/6/11 in)

| Variant | Tone 3 in (astro/horse) | 6 in | 11 in | Look |
|---|---|---|---|---|
| Floor post+space only, fillet 0.22 | 0.769 / 0.859 | 0.888 / 0.888 | 0.924 / 0.925 | Spaces read as **dots**, not mesh (rejected) |
| **Yarn ≤ 30%, fillet 0.12 (adopted)** | 0.643 / 0.753 | 0.889 / 0.903 | 0.923 / 0.928 | Square filet mesh |
| Yarn ≤ 25%, no fillet | 0.638 / – | – | – | Coarser still; stopped |

The 3 in tone drop is a deliberate trade. The brief prefers a larger stitch grid at small sizes, and the adopted result is still far above 560 (0.39).

Two bugs were found and fixed while measuring:

- A block drawn as the window only touched a same-colour diagonal neighbour at a pixel corner. That gave 105 inspector findings at 3 in.
- Fillets against another sheet's post left 45° spikes. That gave 3214 opening residues at 11 in.

### Before/after: all photos, finished geometry (cut-ready sheets)

There are 9 subjects: the 8 photos plus a 0.70-aspect astronaut portrait crop. Values are means per artwork, 560 → M5.

| Size | Tone corr | SSIM | dE OKLab | Holes lost in cut stage | Inspector findings (gen / cut-ready) | Necks | Narrow-vinyl residue | Narrow-waste slits | Smallest hole mm² | Vertices | Cut-stage ms |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 3 in | 0.381 → 0.634 | 0.318 → 0.354 | 0.200 → 0.144 | 53.9 → **0** | 383 / 25.8 → **0 / 0** | 9.4 → 0 | 13.4 (9.9 mm²) → 0 | 16.4 → 0 | 3.9 → 6.8 | 6745 → 2691 | 4723 → 57 |
| 4 in | 0.458 → 0.756 | 0.319 → 0.420 | 0.196 → 0.118 | 224 → **0** | 1490 / 235 → **0 / 0** | 90.7 → 0 | 69 (69 mm²) → 0 | 6.8 → 0 | 3.5 → 4.7 | 11410 → 6506 | 7098 → 178 |
| 6 in | 0.605 → 0.833 | 0.369 → 0.573 | 0.187 → 0.105 | 515 → **0** | 3086 / 440 → **0 / 0** | 164 → 0 | 124 (118 mm²) → 0 | 22.2 → 0 | 3.4 → 4.7 | 26100 → 14430 | 19260 → 381 |
| 8 in | 0.694 → 0.865 | 0.442 → 0.630 | 0.180 → 0.099 | 675 → **0** | 5819 / 2747 → **0 / 0** | 971 → 0 | 887 (820 mm²) → 0 | 197 → 0 | 3.4 → 6.7 | 42230 → 19690 | 24040 → 280 |
| 11 in | 0.776 → 0.884 | 0.556 → 0.676 | 0.177 → 0.097 | 860 → **0** | 10370 / 6609 → **0 / 0** | 2434 → 0 | 2082 (1744 mm²) → 0 | 557 → 0 | 3.3 → 9.7 | 70880 → 25810 | 27880 → 297 |

- **Tone** improved in all 45 cases.
- **SSIM** dropped in 7 of 45: moon at 3/4/8/11 in, rocket at 3/4 in and gravel at 3 in. All are images whose dark backgrounds the 560 output left as bare L1. The M5 mesh adds grid texture there.
  - Visually, the M5 moon shows the craters and 560 does not (see the gallery). This is recorded, not hidden.
- **Holes.** 560 lost 32–68% of its holes in the cut stage, which matches the M4 report's ~50%. M5 loses none.
- **Hole count.** M5 also has 3–6× fewer holes (larger squares, solid blocks), and every hole is a weedable space of at least 1.9 mm.
- **Timing.** Render time rose from about 0.1–0.4 s to 0.2–0.5 s on a loaded host. The cut stage fell from 5–28 s to under 0.4 s, so the 45 s budget risk is gone for Crochet.
- **Gallery.** Side-by-side images and `metrics-*.tsv` are in `~/Downloads/VinylStudio-561-gallery/crochet/` (not in git).

Probe definitions are in `tests/M5CutGeometryProbe.java` (JTS, exact):

- *narrow*: opening residue by min/2 that is thicker than 0.08 mm. This excludes the ordinary trimming of convex 90° corners.
- *necks*: extra eroded cores per piece.
- *narrow waste*: closing residue by gap/2 that is thicker than 0.05 mm.
- *tiny holes*: below (space min)².

The per-photo table is in the gallery folder (`metrics.tsv`).

## B. Contour-smoothing audit

### Exporters (read-only)

Suite styles store `StyleKit.Layer.loops` as polylines and write them as M/L paths. The DXF R13 writer emits flattened POLYLINEs. `SvgDxfR13Exporter` flattens `C`/`Q` only when it parses other SVG paths. The final gate flattens at 0.02 mm.

True curve fitting for suite styles would therefore need a new curve layer model. That is out of scope, so smoothing must emit dense, flattened polylines.

### Style-by-style

| Style | How contours are made | Smoothing today | Decision |
|---|---|---|---|
| Woodland/Desert/Urban camo 35/40/41 | Binary raster, conditioned, marching squares (0/45/90° only) | **None**. Prep's conditioner re-traces only the sheets it repairs, so smoothing is inconsistent across sheets | De-staircasing pass added; opt-in (see below) |
| Digital camo 42 | Same, orthogonal kernels | None | Must stay pixel-square: excluded, and a test proves it |
| Crochet 39 | Pixel-exact rectilinear lattice | – | Rectilinear by design; only deliberate fillets |
| Fishnet 37 | JTS vector capsules on device; raster plus `polishRasterMotifs` on the host | Vector | Unchanged |
| Organic Flow 43–45 | Raster plus `OrganicFlowCutSmoother.contour` at 50% default | Yes, but budgeted: 56 first-pass + 18 large + 8 medium loops per sheet. Sheets with hundreds of loops keep many staircases | Unchanged; finding recorded |
| Poster Stencil 47 | `PopPosterEngine.trace` + 2 Chaikin passes | Yes | Unchanged |
| Tonal stencil (`MultilayerStencilEngine`) | `SmoothStencilContours` cubic curves, flattened by the exporter | Yes | Unchanged |

### Camo de-staircasing (`OrganicFlowCutSmoother.destairOrganicContours`)

The pass applies the same rule to every overlay contour on every sheet. L1 and Digital camo are never touched.

- **Smoothing.** Gaussian smoothing along the arc length, sigma 1.6 px at the 50% default.
- **Bounded movement.** No sample moves more than half a pixel, which is the staircase's own quantisation error.
- **Snip pinning.** Samples within about 1.5 px of the Snip or artboard edge are pinned, so an overlay cannot leave L1.
- **Corners.** Turns above 50° over ±2.5 px are pinned (Urban breakup stays angular).
- **Simplification.** RDP at 0.02 mm or less.
- **Inward moves.** A move into the vinyl is allowed only where another overlay sheet also covers that vinyl. Elsewhere, only the outward component is kept.
- **Validation.** Each loop must keep its orientation, stay simple and stay inside the area band. Each sheet must gain no inspector finding away from an existing one; otherwise nearby loops are reverted, then the whole sheet.

Woodland, astronaut, 6 in. "Off" is the 560 output.

| Variant | Stair frac. | Excess turn (rad/mm) | Grid-segment length | Verts | Min sheet IoU | Newly exposed L1 |
|---|---|---|---|---|---|---|
| Off | 0.522 | 0.689 | 0.642 | 11410 | – | – |
| Symmetric smoothing | 0.029 | 0.291 | 0.070 | 10892 | 0.977 | **17.8 mm², 131 slivers** |
| Outward-only (material-preserving) | 0.196 | 0.710 | 0.268 | 19589 | 0.991 | 0.26 mm², 1 |
| **Backed inward (shipped, opt-in)** | 0.028 | 0.354 | 0.097 | 17570 | 0.982 | 1.35 mm², 30 |

What was rejected and why:

- **Symmetric smoothing** opens hairlines of L1 wherever two sheets abut exactly.
- **Outward-only** does not remove the jaggedness. Excess turning is unchanged, and vertices rise 70%.

What remains with backed-inward:

- 1.35 mm² of slivers up to about 0.15 mm wide, where two overlapping sheets both retreat at the same place.
- Vertices are 54% higher.

Neither is an export hazard. A 0.15 mm sliver is below Cricut registration error, and the vertex density is still bounded. But the "no background contamination" invariant is not *proven*, so the pass ships **off by default** (`Options.contourSmoothing = 0`). With it off, camo goldens are unchanged.

To enable it, the pass needs to be made shared-edge aware: smooth the per-pixel *visible-sheet label map* once, so both sides of an edge take the identical curve. Then set the default to `OrganicFlowCutSmoother.DEFAULT` and wire the slider. That is an integrator/product decision.

Cut-ready finding counts did not discriminate between the variants. Two identical "off" runs gave 7 and 0, because `prepareForCutting` had a 45 s wall-clock budget (replaced by a deterministic work budget later in 561) under host load. This is the open M4 item "remove the wall-clock budgets from geometry".

## Tests

`tests/run-v3-m5-crochet-smoothing-gate.sh` runs two tests.

**`M5CrochetGeometryTest`**, on the real engine and finished geometry, covering:
- sizes 3, 6 and 11 in, an 11 in portrait, and horse at 4 in;
- layer counts 2–9;
- Detail 0 and 100;
- minimums of 0.65 and 1.10 mm;
- an elliptical Snip;
- flat grey and flat black photos;
- determinism, lattice size adaptation, and both open and block stitches present;
- rejection of NaN, negative and too-small inputs and of an invalid style ID;
- the legacy single-stitch primitive.

**`M5CamoDestairTest`**, covering:
- a synthetic staircase, with Hausdorff distance ≤ half a pixel;
- default off, so the suite output is bit-identical;
- for Woodland, Desert and Urban:
  - L1 untouched;
  - staircase reduced at least 5×;
  - loop count and nesting kept;
  - IoU ≥ 0.97;
  - nothing beyond L1;
  - exposure under 0.02% of the artwork;
  - no new findings;
- Digital camo unchanged;
- smoothing setting range check.

Benches (not gates): `tests/M5CrochetBench.java` and `tests/M5SmoothingBench.java`.

## Needs physical verification

All of these are on a Cricut Explore Air 2:

- Crochet at 3 in and at 11 in portrait: weeding of 1.9 mm spaces, and 0.94 mm posts lifting from the carrier.
- Registration of 9 stacked sheets: posts overlap only by one post width.
- A Design Space import of a 9-sheet Crochet export.

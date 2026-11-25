# M5 line engines: dash-length error diffusion (Product 561)

Evidence level: host JVM only (levels 1-2 in `docs/QUALITY_GATES.md`). Nothing here was run on a
phone, imported into Design Space or cut.

## Question

At 2.54 mm pitch and a 0.8 mm cut minimum a ribbon covers at least 0.8/2.54 = 31 % of its lane, so
every sample whose requested width is below the minimum was dropped and highlights went to paper.
M4 measured that the older "highlight retention" fix lowered likeness (tone 0.59 -> 0.44). The M4
recommendation was to replace dropped sub-minimum width by minimum-width dashes chosen by 1-D error
diffusion along each line.

## Algorithm (`BanknoteEngravingEngine.DashModulator`)

A run-length-limited sigma-delta (pulse-density) modulator along one traversal.

- Each sample is HARD (outside artwork/margin/Snip), FIXED (the legacy engine keeps it as a ribbon at
  its own legal width) or SOFT (legal position, requested coverage below what a legal ribbon gives).
- A SOFT sample is cut at a legal width or left as paper. Its duty d (0..1) is the fraction that must
  look covered, shaped as d^gamma, gamma = 1 + 4(1 - strength).
- The running error e = sum(d) - sum(cut steps) is carried along the line. A dash starts at e >= half
  a minimum dash. A dash of k samples covers k-1 steps, so e is credited one step when it ends.
- Constraints:
  - stand-alone dash >= max(2 x minimum, minimum run);
  - every paper gap between pieces of a line >= weed gap + 0.15 mm (flat-end corner margin);
  - a shorter gap in front of a FIXED ribbon is filled, merging into the ribbon;
  - hysteresis: a FIXED ribbon is never split, and its continuation stays attached while e says it
    is covered;
  - a dash cut short by a HARD sample before its minimum length is dropped.
- |e| is clamped, so the long-run cut fraction equals the mean duty (measured exactly: duty 0.05..0.9
  -> cut fraction 0.050..0.900 over 4000 samples, `M5DashModulatorTest`).
- Shape budget: once a sheet holds 4400 pieces no new dash starts and the sheet is regenerated with
  1.8x longer dashes (twice), then with the pre-M5 geometry. Deterministic, no time budget.
- Deterministic phase per line (golden-ratio stagger), so neighbouring lines do not band.

Compared with independent per-segment thresholding (the legacy rule: drop below the minimum), the
modulator conserves tone. Compared with the earlier fixed-rhythm "highlight dashes" (8.5 mm period,
duty 0.30-0.82), coverage goes to zero in white and follows tone continuously.

## Routes checked before changing anything

| Route | How a user gets it | Engine |
|---|---|---|
| Classic | new project (`Settings.defaults`, 2.54 mm) | `ClassicLineArtEngine` (frozen v2.24), threshold |
| Picker | choosing a line style in Styles (`SmartHalftonePresets` + photo suggestion) | modern `LineArtEngine.parallel` + `LinePresenceTracker` (presence 0.65, highlight retention 0.48, opaque budget) |
| Spot | same, SPOT mode | modern engine, presence 0.55 |
| Banknote | suite style 9 | `BanknoteEngravingEngine.pass`; width = min + (max-min) * tone^0.85, never below min |

The new-project default minimum is 0.254 mm (`StudioActivity.newProjectSettings`); 0.8 mm is a
material-profile value. Both were measured.

## Results: Lines (rejected, not enabled)

Prototype at commit `1b7431b` wired the modulator into both line engines behind a default-off
setting. `tests/M5LineDiffusionBench.java`, 6 in, 0.8 mm / 0.5 mm, astronaut, chelsea, camera, coffee,
Variable-width lines. Means:

| Route / variant | tone | SSIM | OKLab dE | err mid | pieces |
|---|---|---|---|---|---|
| Classic baseline | **0.743** | **0.641** | 0.284 | 0.264 | 1131 |
| gamma 1, all tones | 0.510 | 0.514 | **0.185** | 0.114 | 2208 |
| gamma 2 | 0.571 | 0.505 | 0.198 | 0.136 | 2514 |
| gamma 3 | 0.629 | 0.510 | 0.214 | 0.163 | 2126 |
| gamma 4 | 0.662 | 0.524 | 0.227 | 0.182 | 1843 |
| gamma 1, duty floor 0.7 (near-threshold only) | 0.622 | 0.572 | 0.208 | 0.151 | 1274 |
| gamma 1, duty floor 0.85 | 0.705 | 0.593 | 0.241 | 0.201 | 1066 |
| K sheet only, gamma 3 | 0.687 | 0.532 | 0.236 | 0.195 | 1569 |
| Picker baseline | **0.679** | **0.564** | 0.212 | 0.173 | 1014 |
| Picker gamma 2..4 | 0.649..0.667 | 0.548..0.558 | 0.198..0.206 | | 957..1239 |
| Spot baseline | **0.428** | **0.516** | 0.280 | 0.184 | 603 |
| Spot gamma 2..4 / floor 0.85 | 0.286..0.387 | 0.445..0.495 | 0.271..0.275 | | 552..1049 |

A coarser viewing scale (blur ~3 mm at 6 in, `tone_coarse`) gives the same ranking (classic 0.765 vs
0.519..0.726). Size sweep 3/6/11 in, all seven styles: see the table appended below.

Size sweep (astronaut, all seven picker line styles, 3 / 6 / 11 in, strength 0.5 = gamma 3):
tone fell in 63 of 63 style/size/route cases. Means by route and size, tone (SSIM):

| Route | 3 in | 6 in | 11 in | max SVG elements per colour with dashes |
|---|---|---|---|---|
| Classic | 0.506 -> 0.439 (0.403 -> 0.341) | 0.737 -> 0.687 (0.611 -> 0.556) | 0.854 -> 0.826 (0.665 -> 0.678) | 2407 |
| Picker | 0.551 -> 0.539 (0.433 -> 0.424) | 0.737 -> 0.731 (0.606 -> 0.605) | 0.837 -> 0.833 (0.674 -> 0.677) | 1079 |
| Spot | 0.278 -> 0.185 (0.385 -> 0.342) | 0.475 -> 0.366 (0.494 -> 0.434) | 0.616 -> 0.516 (0.533 -> 0.479) | 2066 |

Cut findings with dashes were 0 at 6 in (four photos) and at 3-11 in for Classic Variable-width.

Every variant lowers likeness on every route; tone correlation falls monotonically as dash coverage
grows. Absolute tone (dE, mid-tone error) improves by up to 35 %. Visual review
(`~/Downloads/VinylStudio-561-gallery/lines/`): the classic CMYK and spot line look depends on the
threshold contrast. Highlight dashes in four opaque CMYK colours read as coloured speckle in sky, wall
and skin, not as lighter tone; even a 3 mm viewing blur does not integrate a dash pattern whose
period is 5-20 mm in highlights. Faces lost definition. This is the same physical limit M4 found
(31 % floor at 2.54 mm): a 1.6 mm minimum dash plus a 0.65 mm gap cannot make fine enough texture.

Decision: not enabled for any line style. The prototype wiring was removed again; `LineArtEngine`,
`ClassicLineArtEngine` and `VinylEngine` are byte-identical to 560. Re-run the experiment from
`1b7431b` (`-Dm5.dash`, `-Dm5.gamma`, `-Dm5.floor`, `-Dm5.konly`).

## Results: Banknote Engraving (adopted)

Banknote never drops a sample: width = min + (max - min) * fill. At 0.8 mm the widest ribbon is
1.155 mm, so width can only vary 0.8..1.155 mm: the 560 output is a near-uniform screen
(tone 0.05-0.22 at 6 in). Diffusion of the tones below 0.45 dark into dashes:

6 in, 0.8 mm, four photos (research build, strength 0.5 = gamma 3 / 1.0 = gamma 1):

| Variant | tone 560 | tone g3 | tone g1 | SSIM 560 -> g3 | dE 560 -> g3 | pieces 560 -> g3 |
|---|---|---|---|---|---|---|
| Straight | 0.217 | **0.788** | 0.715 | 0.522 -> 0.572 | 0.191 -> 0.235 | 113 -> 523 |
| Contour flow | 0.081 | **0.776** | 0.692 | 0.485 -> 0.565 | 0.199 -> 0.247 | 113 -> 534 |
| Highlight dashes | 0.630 | **0.788** | 0.715 | 0.391 -> 0.572 | 0.196 -> 0.235 | 771 -> 523 |

Adopted strength 0.75 (gamma 2), 6 in, 0.8 mm, astronaut + camera + chelsea, production suite route
with the cut-ready stage (`lde_bk6` in the gallery):

| Variant | tone 560 -> M5 | SSIM | coarse SSIM | dE | pieces | findings |
|---|---|---|---|---|---|---|
| Straight | 0.253 -> **0.824** | 0.528 -> 0.554 | 0.400 -> 0.708 | 0.182 -> 0.227 | 116 -> 709 | 0 -> 0 |
| Contour flow | 0.105 -> **0.810** | 0.488 -> 0.543 | 0.350 -> 0.693 | 0.190 -> 0.238 | 116 -> 725 | 0 -> 0 |
| Cross-etched* | 0.105 -> **0.810** | 0.488 -> 0.543 | 0.350 -> 0.693 | 0.190 -> 0.238 | 116 -> 725 | 0 -> 0 |
| Wavy | 0.110 -> **0.802** | 0.485 -> 0.527 | 0.351 -> 0.701 | 0.190 -> 0.229 | 117 -> 869 | 0 -> 0 |
| Highlight dashes (strength 0.75, not adopted) | 0.666 -> 0.824 | 0.357 -> 0.554 | 0.675 -> 0.708 | 0.192 -> 0.227 | 895 -> 709 | 0 -> 0 |
| Vignette | 0.179 -> **0.601** | 0.512 -> 0.546 | 0.379 -> 0.614 | 0.186 -> 0.308 | 116 -> 519 | 0 -> 0 |
| Two-ink* | 0.111 -> **0.810** | 0.489 -> 0.544 | 0.351 -> 0.693 | 0.190 -> 0.239 | 116 -> 725 | 0 -> 0 |

\* At 0.8 mm the cross-etch sheet is empty before and after (cross strokes are half the primary
width, so always below 0.8 mm; pre-existing), which is why these rows equal Contour flow.

Size sweep, astronaut, 0.8 mm, strength 0.75, tone (SSIM) 560 -> M5, findings 0 -> 0 everywhere:

| Variant | 3 in | 4 in | 8 in | 11 in (portrait) | pieces 11 in |
|---|---|---|---|---|---|
| Straight | 0.049 -> 0.622 (0.20 -> 0.39) | 0.087 -> 0.683 | 0.593 -> 0.816 | 0.672 -> 0.834 (0.46 -> 0.58) | 126 -> 1097 |
| Contour flow / Cross-etched | -0.032 -> 0.607 | -0.023 -> 0.664 | 0.476 -> 0.799 | 0.669 -> 0.834 | 126 -> 1251 |
| Wavy | -0.025 -> 0.594 | -0.016 -> 0.652 | 0.489 -> 0.800 | 0.689 -> 0.840 | 126 -> 1374 |
| Two-ink | -0.030 -> 0.608 | -0.020 -> 0.665 | 0.520 -> 0.809 | 0.714 -> 0.847 | 126 -> 1251 |
| Vignette | 0.004 -> 0.419 | 0.026 -> 0.449 | 0.320 -> 0.503 | 0.360 -> 0.512 | 126 -> 955 |
| Concentric (unchanged) | 0.050 | 0.087 | 0.645 | 0.815 | 166 |

Single-photo size sweep (astronaut, Straight, 0.8 mm), tone 560 -> gamma 3 / gamma 2:
3 in 0.049 -> 0.655 / 0.622; 4 in 0.087 -> 0.709 / 0.683; 6 in 0.200 -> 0.771 / 0.754;
8 in 0.593 -> 0.827 / 0.816; 11 in 0.672 -> 0.842. Findings 0 before and after, every size.

6 in, 0.254 mm (the new-project default), four photos, strength 0.5:

| Variant | tone 560 -> dash | SSIM 560 -> dash | dE 560 -> dash |
|---|---|---|---|
| Straight | 0.506 -> 0.757 | 0.650 -> 0.653 | 0.185 -> 0.242 |
| Contour flow | 0.764 -> 0.867 | 0.686 -> 0.692 | 0.210 -> 0.274 |
| Cross-etched | 0.840 -> 0.900 | 0.741 -> 0.726 | 0.185 -> 0.250 |
| Wavy | 0.734 -> 0.819 | 0.655 -> 0.647 | 0.231 -> 0.284 |
| Highlight dashes (strength 0.5) | 0.622 -> 0.757 | 0.457 -> 0.653 | 0.225 -> 0.242 |
| Highlight dashes, adopted strength 1.0 | 0.622 -> **0.732** | 0.457 -> **0.646** | 0.225 -> **0.215** |
| Two-ink | 0.855 -> 0.895 | 0.756 -> 0.730 | 0.189 -> 0.247 |
| Vignette | 0.482 -> 0.534 | 0.669 -> 0.599 | 0.235 -> 0.337 |

dE gets worse everywhere (output lighter overall); SSIM is mixed at 0.254 mm.

Visual review: at 0.8 mm the 560 sheets are unrecognisable; with dashes head, hair, collar, helmet and
the camera operator read clearly. Gamma 3 scores 0.01-0.03 higher than gamma 2 but leaves the face
interior almost blank; gamma 2 (strength 0.75) keeps eyes and mouth as sparse dashes, so **strength
0.75 (gamma 2) is the default**.

### Adopted rule (`BanknoteEngravingEngine.diffusionStrength`)

- Highlight dashes variant (4): always diffused at strength 1.0 (linear duty), replacing its fixed
  8.5 mm rhythm. Its promise is highlight dashes; strength 1.0 keeps visibly more dashes in light
  tones, so it stays distinct from Straight engraving when both are diffused at 0.8 mm (tested).
  Measured strength 1.0 vs 560: 0.8 mm, 6 in, four photos: tone 0.630 -> 0.715, SSIM 0.391 -> 0.518,
  dE 0.196 -> 0.196; 0.254 mm: tone 0.622 -> 0.732, SSIM 0.457 -> 0.646, dE 0.225 -> 0.215.
- Every other line variant: diffused automatically only when minimum >= 0.5 x widest ribbon, i.e. when
  ribbon width can no longer carry tone (0.8 mm material profiles). At 0.254 mm they are byte-identical
  to 560 (tested), which keeps their continuous-line character and Straight distinct from Highlight
  dashes. The 0.254 mm gains above are a product option, not enabled.
- Concentric portrait and the cross-etch sheet: never (rings and shadow-only strokes).
- `Options.highlightDiffusion`: -1 automatic (default), 0 pre-M5, >0 forced strength. It is not a
  saved key and no UI exposes it; `VinylStyleSuite` does not set it.

## Cut safety and Snip

- `M5BanknoteDashTest`, every variant, 3/6/11 in portrait, 0.8 and 0.254 mm: `StyleCutGeometryPreflight`
  findings are never above 560 and are 0; max pieces per colour 1418 (cap 4400, Cricut 5000).
- Dense grey 11 in sheet at the finest pitch exercises the shape-budget regeneration.
- Snip: the mask makes samples HARD; dashes are cut only by the Snip boundary itself. Coverage inside
  a circular Snip (eroded 4 mm) equals the un-snipped render (0.915 vs 0.916).
- All-dark image: geometry identical with and without diffusion. All-white: nothing cut.
- NaN, +-inf, < -1 and > 1 strengths are rejected by `Options.validate`.

## Slider audit (`M5LineSliderEffectTest`)

211 slider/style/route cases (picker and Classic routes, all 12 line styles, UI ranges from
`StudioActivity`). All change geometry except these controls, which the UI shows but the engine
provably ignores (needs UI gating in `StudioActivity`, not an engine change):

- "Contour smoothing" on Straight, Wavy, Crosshatch, Zigzag, Broken, Fade, Beaded, Rings, Rays
  (lineSmoothing only filters the luminance bend of Variable-width, Contour and Hatch);
- "Follow form (bend)" on Straight, Wavy, Fade (contourStrength bends only those three);
- "Micro-gap recovery" on Classic routes (Variable-width, Straight, Contour, Wavy, Zigzag): the Classic
  engine has no micro-gap stage;
- "Light-area lines" on the picker route for Beaded, Rings, Rays.

## Physical checks still required

1. Weeding a 0.8 mm Banknote sheet with >= 2.4 mm dashes (minimum run 3 x 0.8 mm) and >= 0.71 mm (0.56 + 0.15) end gaps.
2. Transfer-tape lift of isolated short dashes in highlights.
3. Whether a 0.254 mm material cuts the Highlight-dashes variant's 1.4 mm x 0.254 mm dashes cleanly and keeps them on the backing.
4. Design Space import of a sheet with ~1000 pieces per colour (time, no merging).

## Remaining defects (not changed here)

- Concentric portrait at 0.8 mm is still a near-uniform ring screen at 3-4 in (tone 0.05-0.09); its
  closed-ring path was left alone.
- Cross-etched / Two-ink: at 0.8 mm the cross-etch sheet is empty (cross strokes are half the primary
  width, always below the minimum). Pre-existing.
- `GoldenGeometryHostTest` (not in the manifest) fails before and after this change with
  "Golden case count changed: 229 != 268". Its `style-9-4-*` rows (Highlight dashes at 0.35 mm) will
  differ by design when that test is re-blessed; all other Banknote rows are unchanged (minimum
  0.35 mm < 0.5 x 1.155 mm widest ribbon).

## Integrator requests

- Register `tests/run-v3-m5-lines-gate.sh` in `tests/gates.manifest` and run `scripts/sync-gates.py`.
- `StudioActivity` slider gating (see the slider audit): show "Contour smoothing" only for
  `LINE`, `CONTOUR`, `HATCH`; "Follow form (bend)" in `shapeControls()` only for `LINE`, `CONTOUR`,
  `HATCH`; "Micro-gap recovery" only when `!settings.classicCmykLines`; "Light-area lines" on the
  modern route only for parallel ribbon styles. Then delete the matching `KNOWN_NO_OP` entries in
  `tests/M5LineSliderEffectTest.java` and mirror the new conditions there.

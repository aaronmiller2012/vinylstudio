# M5 colour engineering (build 561)

Scope: palette ownership and colour state, Confetti base selection, quantisation space, per style family decisions.
All numbers are host-JVM measurements on the eight CC0 photos in `tests/m4/photos`, opaque stacked composites,
viewing-scale OKLab dE (blur sigma = width/150, the M4 harness). Nothing here has been run on a phone, in Design
Space or on a cutter.

Reproduce: `tests/M5ConfettiBench.java`, `tests/M5PhotoPaletteStyleBench.java`, `tests/M5QuantizerBench.java`,
gate `tests/run-v3-m5-color-gate.sh`.

## 1. Palette path that reaches Confetti

`StudioActivity.newStylePresetForKey` -> (photo key only) `FoundationColorPolicy.anchorPhotoPalette(light=false)` ->
`VinylPaletteMapping.forStyle` -> `PaletteStylePolicy.arrange(CONFETTI_STACK)` (named palettes) ->
`PerceptualLayerColorEngine.finishPreset` -> `VinylStyleSuite` `Config.palette` -> `PatternPortraitTrioEngine` ->
`FallenConfettiEngine.render`. The engine treats `palette[0]` as the solid Snip silhouette and scatters fragments
of `palette[1..]` chosen by tone rank.

Root causes found:

1. **Named palettes: base is the darkest of eight** (`PaletteStylePolicy.tonalWithBase(..., lightBase=false)`).
2. **Photo palettes park the highlights.** The eight swatches are stored dark to light and the first `count`
   are active (`anchorPhotoPalette` only moves one swatch to the front). With the default 6 sheets the two
   lightest photo colours (skin highlights, bright background) are parked. Measured RMS OKLab error of the active
   set against the photo: 0.103; lightest active swatch is 0.161 OKLab L below the photo's 95th percentile.
3. The base shows through between fragments: fragment area coverage is about 0.38 (the base colour is visible in
   62 % of the composite pixels in every benchmark render, 0.62-0.64), so the base dominates the integrated colour.

## 2. Confetti base: measured comparison

Same 6-sheet active set per case, only the base is re-seated (others ascend by lightness). 6 in, mixed shapes,
16 cases (8 photos x {photo palette, Natural Skin}); fragments per render are identical across variants (388-395),
because the base choice does not touch cut geometry.

| Base | tone corr | SSIM | dE (OKLab) | err hi | err mid | err sh |
|---|---|---|---|---|---|---|
| E. as shipped (pre-M5 production flow) | 0.384 | 0.303 | 0.218 | 0.376 | 0.217 | 0.111 |
| A. darkest | 0.298 | 0.212 | 0.254 | 0.404 | 0.257 | 0.132 |
| B. median tone | 0.548 | 0.447 | 0.161 | 0.226 | 0.101 | 0.151 |
| C. perceptual (chosen) | 0.479 | 0.413 | **0.157** | 0.230 | 0.110 | 0.178 |

Photo palette only (8 cases, with the active subset of section 3): E dE 0.188, SSIM 0.403; C dE 0.143, SSIM 0.451.
Named palette (Natural Skin): E = A dE 0.248; B 0.172; C 0.170. Additional 24-case run over Portrait Vinyl, Natural
Skin, Pop Art and the photo palette: A 0.254, B 0.183, C 0.174.

Visual review (`~/Downloads/VinylStudio-561-gallery/confetti/`): the darkest base reads as a dark field with a few
bright chips, so highlights and mid-tones are lost. The median base keeps the subject legible but its median can be
hue-incoherent (Natural Skin gives a grey-green field behind pink fragments). The perceptual base picks a brown that
belongs to the palette and the photo. Chosen: **C**, because it has the best dE, the best SSIM on photo palettes and
the most coherent colour; B has a higher tone correlation (more contrast) and is the fallback when no photo is known.
Trade-off: shadow error is higher (0.111 -> 0.178), since a lighter base shows less deep shadow between fragments.
Faces remain a weak point: with about 390 fragments at 6 in and 38 % coverage a face is only suggested.

Model (`ConfettiBasePolicy`): expected viewing-scale error of reproducing a photo pixel x with base b and best
overlay o at fragment fraction rho is |x - ((1-rho) b + rho o)|^2 in OKLab; rho = 0.38 is the measured fraction
above. A model with rho = 0.60 (my first guess) was worse (dE 0.1955 vs 0.1742), so the constant is measured, not
assumed.

Engine behaviour (`FallenConfettiEngine`) is **unchanged**: solid silhouette base, opaque cross-sheet overlap,
relaxed random placement with clustered satellites (no grid), all six shape modes. No golden uses this ordering.

## 3. Photo palette: active subset (`PhotoSwatchSubset`)

Exhaustive search over C(8,k) subsets minimising RMS OKLab error against the photo. 8 photos x k = 4..7:

| | RMS dE | highlight gap (p95 L - lightest active) |
|---|---|---|
| first-N (pre-M5) | 0.1029 | 0.161 |
| subset (M5) | 0.0409 | 0.014 |

Applied to Confetti (explicit photo apply only). Rendered through Prismatic Diagonal (6 in, 6 sheets, all photos) it
is **mixed**: dE 0.0622 -> 0.0610, tone corr 0.851 -> 0.859, SSIM 0.724 -> 0.691, highlight error 0.173 -> 0.066,
shadow error up. Forcing the darkest and lightest swatch to stay active was worse (dE 0.0676, SSIM 0.671). So it is
**not** applied to Prismatic or any other family. The gap exists there too; a Prismatic specific selection is an
open item for the Prismatic owner.

## 4. Quantisation space (stencil)

`SourceColorStencilQuantizer` clusters in CIELAB (dE76). Experimental OKLab clustering (`quantize(..., oklab=true)`,
not the default), k = 3, 4, 6, 8 on eight photos (32 cases):

| space | mean dE | p95 dE | mean abs dL | skin dE | neutral pixels: mean cluster chroma |
|---|---|---|---|---|---|
| CIELAB (production) | 0.0414 | 0.0928 | 0.0391 | 0.0326 | 0.0160 |
| OKLab | 0.0352 | 0.0742 | 0.0297 | 0.0278 | 0.0215 |

The OKLab figures are measured in OKLab, which favours OKLab clustering by construction. Neutrals pick up more
chroma (12 of 32 cases above 0.03 against 7 of 32). Changing the default changes stencil geometry, cluster order and
every saved tonal project, as ADR-0005 section 5 already recorded, so the default is **kept**. Palette matching
(`PhotoSwatchAssociation`, used by stencil-to-vinyl, Confetti and most families) already works in OKLab with a
lightness weight of about 1.8, a chroma guard against opposite hues and photo-range normalisation. No migration needed.

## 5. Per style family decision

| Family | Decision |
|---|---|
| Confetti (27, 30-34) | Perceptual base + photo subset (this change). |
| Prismatic | Unchanged. Light foundation is correct for dark ribbons; subset is mixed, see section 3. |
| Tonal Stencil | Keep CIELAB k-means default; OKLab evaluated and rejected as default (section 4). |
| Multilayer stencil | Matching already OKLab-based. No change. |
| Linocut, Layered/Offset cells, Poster, other overprint | Not changed; owned elsewhere or no measured defect. |

## 6. Colour-state invariants (behavioural, `M5ColorStateTest`)

10 styles x 30 presets x 2..8 sheets x 6 mounting surfaces x reverse on/off: the mounting surface never changes a
photo-derived palette, nor a named palette for surface-independent families; no swatch is lost or recoloured;
Reverse is an involution for `reverseActive`, `orientCmykActive` and `PhysicalLayerColorReverse`; parked swatches and
the structural base never move; the default classic CMYK stack is Yellow L1 ... Black uppermost and Reverse keeps
both; export file names and labels carry the edited colour hex and stack index. No defect found, so ADR-0006 is
resolved as option A (wording only).

## 7. Integration (StudioActivity, owned by the integrator)

In `newStylePresetForKey` pass the prepared photo to the confetti path, and only for explicit apply:

```java
int[] mapped=VinylPaletteMapping.forStyle(newStyleId,Math.min(8,count),settings.paper,colors,false,
    "photo".equals(key)||key.startsWith("saved:"),
    VinylStyleSuite.isConfetti(newStyleId)&&!key.startsWith("saved:")?preparedPhotoColorPixels:null);
```

Saved palettes must pass null so a saved palette is never re-ordered. Named palettes without a photo already get the
median-tone base from the 6-argument overload.

## 8. Open items

- Device/cutter verification of the lighter Confetti base (adhesion/contrast of real vinyl) is not done.
- Shadow error rises with the lighter base; a tone-driven chip density was already rejected in M4.
- Prismatic photo subset selection, Craft and Organic Flow base policy: not evaluated.

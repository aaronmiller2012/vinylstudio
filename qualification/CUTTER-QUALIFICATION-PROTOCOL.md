# Cutter qualification protocol — build 553

Software tests prove the files are internally consistent. Only this protocol proves a
stranger's cut comes out right. Do it once per release that changes geometry, and before
the Play listing claims Cricut or Silhouette compatibility.

**Never type expected values into "observed" cells, and never mark PASS without the cut in hand.**
`python3 tests/check-qualification-status.py` summarizes all three evidence files.

## Equipment

- One Cricut (Maker/Explore/Joy) with current Design Space, and one Silhouette with Silhouette Studio.
- Scrap permanent adhesive vinyl in at least two colors, transfer tape, weeding hook.
- Digital calipers (0.01 mm) or a steel rule (0.5 mm).

## 1. Import scale and registration (≈20 min) → `cutter-measurements.csv`

1. In VinylStudio: *Export → Material calibration → SVG*, then again as *DXF*.
2. Use the new **Share sheets** prompt (or the saved file) to open the SVG in Design Space and the DXF in
   Silhouette Studio. Do **not** resize on import.
3. Measure the reference square and the horizontal/vertical registration spacing **in the cutter app's own
   dimension readout**, then again on the cut vinyl with calipers. Record the cut-vinyl value.
4. Fill `observed_mm`, `cutter_software` (with version) and `device`. Tolerance is ±0.30 mm.
5. `python3 tests/qualify-cutter-import.py qualification/cutter-measurements.csv`

## 2. Physical cut and weed (≈2 h) → `physical-cut-log.csv`

Use one real portrait photo for every row. For each row:

1. Set the style, width and layer count shown. Export the verified cut ZIP and share the sheets to the cutter app.
2. Cut every sheet. Record blade, pressure/force preset, material and the cutter software version.
3. `cut_clean` = PASS only if every sheet cut through without tearing, dragging or missed paths.
4. `weed_clean` = PASS only if small islands and narrow gaps weeded without lifting or tearing.
5. Layer the sheets using the registration marks. Measure the worst visible misalignment between any two
   layers in mm and record it in `registration_offset_mm` (limit 0.5 mm).
6. Note anything that needed a workaround. A FAIL row is useful evidence: keep it, file the defect, and re-run
   after the fix.

## 3. Portrait quality (≈1 h) → `portrait-quality-results.csv`

Follow the *Milestone B* section of `qualification/README.md`, comparing build 552 (baseline) with 553
(candidate), then run `python3 qualification/check-portrait-quality.py qualification/portrait-quality-results.csv`.

## Release decision

- `tests/check-qualification-status.py` exits 0 → cutter compatibility may be claimed for the recorded
  cutters/software versions only.
- Set `VINYL_REQUIRE_QUALIFICATION=1` for the Play bundle build to make missing evidence block the AAB.

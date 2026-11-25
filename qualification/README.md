# Physical qualification

**Start with `CUTTER-QUALIFICATION-PROTOCOL.md` (build 553).** It covers import scale, the physical
cut/weed log and portrait sign-off; `python3 tests/check-qualification-status.py` summarizes the evidence.
The sections below are the original Milestone A/B instructions, kept for reference.

## Milestone A physical cutter qualification — pending

Use the calibration vectors from `VinylStudio-V3-MilestoneA1-Cutter-Calibration.zip`.

1. Build, install, and export Beta 13 on the S22 Ultra. Confirm that both preview and exported cut sheet use the requested layer count and coordinate system at 11 inches. Check nonrectangular Snip with holes and an 8-layer portrait.
2. Import the calibration SVG into Cricut Design Space. Record the **measured actual geometry**, not the empty artboard width; inspect position of all registration marks.
3. On desktop cutter software that supports DXF, import the DXF version and record the reference square and registration spacing. Don't set the DXF selection's bounding box equal to the SVG artboard.
4. Fill the six `observed_mm` cells and the `cutter_software`, `device`, and `notes` columns in `cutter-measurements.csv`. Use the same units: mm. Run `python3 tests/qualify-cutter-import.py qualification/cutter-measurements.csv`.
5. Perform a scrap cut of all actual physical layers, including top/bottom registration, a complex Snip boundary, narrow gaps, and small isolated islands. Record media, blade, pressure, cutter software version, and any slipping/tearing. **A passed measurement table alone is not a physical cutting certificate.**

**Never pre-fill measured values from the SVG/DXF, infer a pass from a host gate, or claim a qualified cutter without hardware evidence.** If the device software changes units or scale, keep the original measurement and record corrective steps in notes.

## Milestone B real-portrait sign-off (Beta 17)

For a consistent photo and palette, compare exported finished artwork at 6, 11 and
15 inches using Shapes, Lines, Multilayer and Tessellations, at 8 layers. In
`portrait-quality-results.csv` identify one consistent original photo, palette and Snip across all 12 cases (not a screenshot
of another app), the same baseline/candidate build versions, exactly 8 physical layers, the tester and scores from 0 to 5 for
face recognizability (eyes, nose, mouth, silhouette, hair). Do not use synthetic
edge-fixture scores as human portrait scores. Mark continuity, swatches and
minimum cut checks PASS only after inspection; blank cells fail the gate.
Run `python3 qualification/check-portrait-quality.py qualification/portrait-quality-results.csv`.
The score sheet documents visual engineering acceptance, not clinical or
objective psychometric measurement. Physical cut and weed sign-off remains
separate from quality scoring. Do not invent scores or pass flags.

## Milestone C1 export handoff (Beta 17)

Every cut ZIP includes `cut-complexity.csv`: per-sheet physical dimensions,
closed contours, circles, open drawing paths and the conservative total. A
Cricut SVG with more than 5,000 conservative contours is rejected instead of
silently simplified. Generic SVG and Silhouette DXF retain their device-specific
policies. Import behavior and weedability require hardware qualification.

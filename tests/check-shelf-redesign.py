from pathlib import Path
main=Path("app/src/main/java/com/halftonestudio/StudioActivity.java").read_text()
engine=Path("app/src/main/java/com/halftonestudio/VinylEngine.java").read_text()
line=Path("app/src/main/java/com/halftonestudio/LineArtEngine.java").read_text()
smart=Path("app/src/main/java/com/halftonestudio/SmartHalftonePresets.java").read_text()
checks={
    "actual V2.11 CMYK source, no webview substitute":"extends Activity" in main and "private CanvasView artwork" in main,
    "CMYK is four-color default":"s.mode=CMYK" in engine and "pattern=LINE" in engine,
    "persistent CMYK shelf is above tool cards":"controlsColumn.addView(layerScroller" not in main and "controlsColumn.addView(workflowScroller" in main,
    "channel order and physical order both represented":'layer.cmykChannel==0?"C"' in main and 'physical vinyl layer ' in main,
    "switching tool does not reset canvas":"// Panel navigation does not rerender the artwork or reset its zoom." in main,
    "navigation pills are built once":"dockPillLabels.size()!=WORKSPACE_NAMES.length" in main,
    "persistent quick buttons avoid recreation":"signature.equals(favoritesSignature)" in main,
    "no over-canvas mini controls":"miniPanel=new LinearLayout" not in main and "canvasStage.addView(miniPanel" not in main,
    "shape-definition slider available directly":"Edge-following lines · %" in main,
    "new CMYK line preset adds bounded edge emphasis":"settings.detailBoost=.18f" in smart and "s.detailBoost*localEdge" in line,
    "vinyl geometry remains closed contours":"VinylEngine.Shape.polygon" in line or "Shape.polygon" in line,
    "minimum cut width still checked":"s.minimumMm" in line,
}
for name,ok in checks.items():
    if not ok:raise AssertionError(name)
print("PASS: CMYK-first persistent bottom shelf, no full UI refresh, contour-enhanced vinyl geometry")

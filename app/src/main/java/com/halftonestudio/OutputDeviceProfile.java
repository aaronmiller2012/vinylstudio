package com.halftonestudio;

/**
 * Output handoff presets, not hardware drivers or machine safety certifications.
 * Deliberately no device commands, guessed media sizes, forced mirror, blade depth,
 * laser power, registration calibration or automatic scale transformations.
 */
public final class OutputDeviceProfile {
    public static final int CRICUT=0;
    public static final int SILHOUETTE_DESIGNER=1;
    public static final int BROTHER_CANVAS=2;
    public static final int GENERIC_SVG=3;
    public static final int DESKTOP_PRINTER=4;
    public static final int SILHOUETTE_BASIC=5;
    public static final int GRAPHTEC_STUDIO=6;
    public static final int SISER_LEONARDO=7;
    public enum CutFormat { SVG, DXF_R13, PRINT_PDF }
    private static final Profile[] PROFILES={
        new Profile(CRICUT,"Cricut · Design Space",true,"Cricut Design Space","SVG per color; import at original physical size.",
            "Fastest: upload 00-ALL-LAYERS-one-upload.svg once; Design Space keeps every color as its own layer and puts each on its own mat. Or import the separate SVG sheets into Design Space. Use SVG on Android and iOS; DXF import is not supported there. Select each color sheet and its registration contours together and use Attach to preserve relative placement on the mat. Verify imported dimensions. Keep the same artboard dimensions and placement on all sheets."),
        new Profile(SILHOUETTE_DESIGNER,"Silhouette · Designer Edition+",true,"Silhouette Studio Designer Edition or higher","SVG import requires Designer Edition or higher.",
            "Open individual SVG sheets in Silhouette Studio Designer Edition or higher. Basic Edition does not import SVG; select the separate Basic Edition DXF destination instead. Verify physical dimensions and per-sheet alignment before cutting."),
        new Profile(BROTHER_CANVAS,"Brother ScanNCut · CanvasWorkspace",true,"Brother CanvasWorkspace","Import SVG into CanvasWorkspace; convert to FCM there.",
            "Import each SVG into CanvasWorkspace; use CanvasWorkspace to convert and transfer FCM as required by your ScanNCut. These SVG sheets are not machine-readable FCM files. Verify original dimensions and registration separately."),
        new Profile(GENERIC_SVG,"Other cutter · SVG import",true,"SVG-compatible cutting software","SVG file handoff only; verify device compatibility.",
            "Import each SVG in your cutter software at its original physical dimensions. Check filled contours, holes, registration positions and material limits. No machine compatibility or commands are certified."),
        new Profile(DESKTOP_PRINTER,"Inkjet / laser printer · PDF",false,"Paper printer / PDF viewer","Print composite PDF at 100% scale; no cut registration.",
            "Export the composite PDF for normal paper printing. It is a presentation image, not a layer-separated cut file or vendor Print Then Cut calibration sheet. Disable automatic fit-to-page and check printed dimensions."),
        // Added at the end so existing persisted destination IDs keep their meaning.
        new Profile(SILHOUETTE_BASIC,"Silhouette · Basic Edition (DXF)",true,"Silhouette Studio Basic Edition","Per-color genuine R13 DXF; Basic Edition does not import SVG.",
            "Open each DXF in Silhouette Studio Basic Edition. FIRST import a 100.000 mm calibration square and measure it in Studio. Confirm the exact scale (or uniformly correct it) before importing layers. After import compare each sheet's actual geometry size to dimensions-dxf.txt: Silhouette may change DXF scale and discard empty artboard margins. Never force a cropped selection to the full artboard width. Re-scale uniformly and preserve registration mark positions. These physical marks are NOT Silhouette optical Print & Cut marks. Inspect contours and test-cut scrap vinyl.",CutFormat.DXF_R13),
        new Profile(GRAPHTEC_STUDIO,"Graphtec · Studio 2 (SVG)",true,"Graphtec Studio 2","SVG per vinyl color; handoff, not native GP-GL commands.",
            "Open each SVG cut sheet in Graphtec Studio 2. Verify size, alignment and contour interpretation. Select compatible cutter and material settings inside Graphtec's software. Do not mistake VinylStudio registration contours for Graphtec optical barcode/mark jobs."),
        new Profile(SISER_LEONARDO,"Siser · Leonardo (SVG)",true,"Siser Leonardo Design Studio","SVG per vinyl color for Juliet/Romeo software import.",
            "In Leonardo Design Studio, use File > Open > Import File and choose each individual SVG. Keep all color layers to the same dimensions, preserve their registration positions and verify that filled ribbons remain cut contours. Test first on scrap material.")
    };
    public static final class Profile {
        public final int id;
        public final String name;
        public final boolean svgCutZip;
        public final String handoffSoftware;
        public final String summary;
        public final String instructions;
        public final CutFormat format;
        private Profile(int id,String name,boolean svg,String software,String summary,String instructions){
            this(id,name,svg,software,summary,instructions,svg?CutFormat.SVG:CutFormat.PRINT_PDF);
        }
        private Profile(int id,String name,boolean svg,String software,String summary,String instructions,CutFormat format){
            this.id=id;this.name=name;this.svgCutZip=svg;
            this.handoffSoftware=software;this.summary=summary;this.instructions=instructions;this.format=format;
        }
    }
    private OutputDeviceProfile(){}
    public static Profile byId(int id){return id>=0&&id<PROFILES.length?PROFILES[id]:PROFILES[CRICUT];}
    public static String[] names(){String[] out=new String[PROFILES.length];
        for(int i=0;i<out.length;i++)out[i]=PROFILES[i].name;return out;}
    public static String handoffText(int id){
        Profile p=byId(id);
        if(!p.svgCutZip)throw new IllegalArgumentException("A printer PDF is not an SVG cut archive");
        return "VINYLSTUDIO OUTPUT HANDOFF\n"+
            "Target: "+p.name+"\n"+
            "Import using: "+p.handoffSoftware+"\n"+
            "Format: per-color "+(p.format==CutFormat.DXF_R13?"DXF R13":"SVG")+" cut sheets in ZIP\n"+
            "Physical size: "+(p.format==CutFormat.DXF_R13?
                "DXF numeric geometry and $INSUNITS=4 are in millimeters. Silhouette Studio Basic may ignore units or rescale; compare each imported sheet's geometry bounds to dimensions-dxf.txt. Blank artboards may be trimmed; do not force artboard dimensions on the selected cut shapes.\n":
                "Preserve each SVG's millimeter width/height and common artboard coordinates.\n")+
            "Instructions: "+p.instructions+"\n"+
            "Safety: inspect cuts, preserve Snip holes, confirm registration and test on scrap material.\n"+
            "This is a file export only; no device is connected or operated by VinylStudio.\n";
    }
}

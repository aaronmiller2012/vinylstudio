package com.halftonestudio;

import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.charset.StandardCharsets;

/** A single output-format boundary shared by standard and specialty styles. */
final class CutSheetZipWriter {
    private final ZipOutputStream zip;
    private final OutputDeviceProfile.CutFormat format;
    private double widthMm=-1,heightMm=-1;
    private double[] referenceRootFrame;
    private int sheets,openPaths,closedContours,vertices;
    private final StringBuilder geometrySizes=new StringBuilder();
    private final StringBuilder artboards=new StringBuilder("sheet,width_mm,height_mm\n");
    private final java.util.List<FinalCutSheetSafety.Report> physicalReports=new java.util.ArrayList<>();
    private final float minimumCutMm;
    // <=0 selects the legacy strict inspection (weed gap held to minimumCutMm).
    private final float minimumGapMm;
    private final int outputDeviceId;
    // Cricut only: the mat the user selected. Null keeps the legacy report-only labelling.
    private String selectedMat;
    private final StringBuilder complexity=new StringBuilder("sheet,format,physical_width_mm,physical_height_mm,closed_contours,circles,open_paths,conservative_elements,cricut_svg_limit,status,weed_score,weed_rating\n");
    private final boolean reviewApproved;
    private boolean sourceFindings;
    // Build 556: verified SVG sheets retained for the single-upload combined SVG.
    private final java.util.List<CombinedLayerSvg.Sheet> combinedSheets=new java.util.ArrayList<>();
    private long combinedChars;
    private String combinedSkipReason;
    static final long MAX_COMBINED_CHARS=24L*1024*1024;
    CutSheetZipWriter(ZipOutputStream zip,int deviceId){this(zip,deviceId,0.30f,false);}
    CutSheetZipWriter(ZipOutputStream zip,int deviceId,float minimumMm,boolean approved){
        this(zip,deviceId,minimumMm,-1f,approved);
    }
    /** Export writer: kept vinyl is held to minimumMm, weeded waste to gapMm. */
    CutSheetZipWriter(ZipOutputStream zip,int deviceId,float minimumMm,float gapMm,boolean approved){
        this.zip=zip;
        this.minimumGapMm=Float.isFinite(gapMm)&&gapMm>0?gapMm:-1f;this.outputDeviceId=deviceId;this.format=OutputDeviceProfile.byId(deviceId).format;
        this.minimumCutMm=Float.isFinite(minimumMm)&&minimumMm>0?minimumMm:.30f;
        reviewApproved=approved;
        if(format==OutputDeviceProfile.CutFormat.PRINT_PDF)throw new IllegalArgumentException("Printer destinations do not make cut ZIPs");
    }
    /** The selected Cricut mat is authoritative: a sheet that does not fit it is rejected. */
    CutSheetZipWriter selectMat(String mat){
        if(sheets!=0)throw new IllegalStateException("Select the mat before writing sheets");
        selectedMat=CricutMatPreflight.normalizeMat(mat);return this;
    }
    String selectedMat(){return selectedMat;}
    void noteSourceFindings(boolean hasFindings){sourceFindings|=hasFindings;}
    int criticalFindingCount(){int n=0;for(FinalCutSheetSafety.Report r:physicalReports)n+=r.critical;return n;}
    int narrowFindingCount(){int n=0;for(FinalCutSheetSafety.Report r:physicalReports)n+=r.narrow;return n;}
    int unknownFindingCount(){int n=0;for(FinalCutSheetSafety.Report r:physicalReports)n+=r.unverified;return n;}
    boolean isDxf(){return format==OutputDeviceProfile.CutFormat.DXF_R13;}
    int reviewSheetCount(){int n=0;for(FinalCutSheetSafety.Report r:physicalReports)if(r.needsReview())n++;return n;}
    int criticalSheetCount(){int n=0;for(FinalCutSheetSafety.Report r:physicalReports)if(r.critical>0)n++;return n;}
    int unknownSheetCount(){int n=0;for(FinalCutSheetSafety.Report r:physicalReports)if(r.unverified>0)n++;return n;}
    String fileName(String svgName){
        if(svgName==null||!svgName.toLowerCase(Locale.ROOT).endsWith(".svg"))
            throw new IllegalArgumentException("Cut sheet must be SVG source");
        return isDxf()?svgName.substring(0,svgName.length()-4)+".dxf":svgName;
    }
    /** "12x12" or "12x24" for a Cricut export after the first sheet; null otherwise. */
    String requiredCricutMat(){
        return outputDeviceId==OutputDeviceProfile.CRICUT&&sheets>0?CricutMatPreflight.requiredMat(widthMm,heightMm):null;
    }
    String manifest(String source){return isDxf()?source.replace(".svg",".dxf"):source;}
    String readme(String source){return isDxf()?source.replace("SVG","DXF").replace("svg","dxf"):source;}
    /** Converts final SVG contours; no bitmap trace, path re-rasterization or device commands. */
    String put(String svgName,String svg) throws IOException {
        String name=fileName(svgName);
        // The public ZIP writer reports unsafe source documents as an IO
        // export failure. Keep the fail-closed SVG preflight, but normalize
        // its validation exception so callers never mistake it for a crash.
        try {
            CutSvgCompatibility.verify(svg,outputDeviceId);
        } catch (IllegalArgumentException unsafeSvg) {
            throw new IOException("Cut sheet rejected ("+name+"): "+unsafeSvg.getMessage(),unsafeSvg);
        }
        // Inspect the physical sheet being written, not a draft render or a
        // style-specific approximation. Unverifiable geometry is reported.
        FinalCutSheetSafety.Report report=FinalCutSheetSafety.inspect(name,svg,minimumCutMm,minimumGapMm);
        // Enforce one physical coordinate system across ALL sheets, not only
        // DXF. Previously SVG sheets could silently disagree in size/position.
        if(!(report.widthMm>0&&report.heightMm>0))
            throw new IOException("Cut SVG is not valid physical vector geometry; layer not exported: "+name);
        // Enforce the actual mat WIDTH including registration, not merely
        // the 11-inch photo. This also covers specialty exporters and DXF
        // adapters that share this writer (only Cricut is constrained here).
        if(outputDeviceId==OutputDeviceProfile.CRICUT)
            CricutMatPreflight.requireFits(report.widthMm,report.heightMm,name,selectedMat);
        if(sheets!=0&&(Math.abs(report.widthMm-widthMm)>.05||Math.abs(report.heightMm-heightMm)>.05))
            throw new IOException("Physical artboard mismatch across layers; registration alignment is unsafe: "+name);
        // Matching width/height is insufficient: a different viewBox offset,
        // scale, or root transform shifts physical placement between sheets.
        if(report.rootFrame==null||report.rootFrame.length!=6)
            throw new IOException("Unverified SVG registration coordinate frame: "+name);
        // NaN affine components would bypass the absolute-difference check
        // below (all comparisons with NaN are false). A cut registration
        // frame must have finite affine coefficients.
        for(double component:report.rootFrame)if(!Double.isFinite(component))
            throw new IOException("Nonfinite SVG registration coordinate frame: "+name);
        if(sheets!=0){
            for(int i=0;i<6;i++){
                double tolerance=i<4?1e-6:.05; // affine scale vs physical translation in mm
                if(Math.abs(report.rootFrame[i]-referenceRootFrame[i])>tolerance)
                    throw new IOException("SVG layer viewBox/registration coordinate frame mismatch: "+name);
            }
        }else referenceRootFrame=report.rootFrame.clone();
        if(report.outsideArtboard)
            throw new IOException("Cut contour extends beyond declared physical artboard; cannot guarantee size: "+name);
        // C1 production handoff: document per-sheet import complexity without
        // deleting or simplifying any geometry to reach an arbitrary budget.
        // The conservative count is deliberately by physical contour, not XML
        // path elements. Compound paths may import differently across cutters.
        long elements=(long)report.contours+report.circles+report.openPaths;
        if(outputDeviceId==OutputDeviceProfile.CRICUT && elements>5000L)
            throw new IOException("Cricut SVG sheet exceeds conservative 5000-contour import budget: "+name+
                " ("+elements+"). Increase spacing or split the artwork; nothing was removed.");
        // Build 554 advisory weeding estimate from the same final geometry; never blocks export.
        WeedDifficulty.Score weed;
        try{
            SvgDxfR13Exporter.SafetyGeometry g=SvgDxfR13Exporter.safetyGeometry(svg);
            weed=WeedDifficulty.score(g.closedLoops,g.circles,g.openPaths,report.widthMm,report.heightMm,minimumCutMm,report.narrow+report.critical);
        }catch(IOException|RuntimeException unparsed){weed=null;}
        complexity.append(String.format(Locale.US,"%s,%s,%.3f,%.3f,%d,%d,%d,%d,%s,%s,%s,%s\n",
            name.replace(',','_'),isDxf()?"DXF_R13":"SVG",report.widthMm,report.heightMm,
            report.contours,report.circles,report.openPaths,elements,
            outputDeviceId==OutputDeviceProfile.CRICUT?"5000":"not_applicable",
            report.unverified>0?"UNVERIFIED":report.critical>0?"REVIEW_REQUIRED":"COUNTED",
            weed==null?"":Integer.toString(weed.value),weed==null?"UNKNOWN":weed.rating));
        physicalReports.add(report);
        widthMm=report.widthMm;heightMm=report.heightMm;
        artboards.append(String.format(Locale.US,"%s,%.3f,%.3f\n",name.replace(',','_'),widthMm,heightMm));
        if(isDxf()){
            SvgDxfR13Exporter.Result converted=SvgDxfR13Exporter.convert(svg);
            if(sheets!=0&&(Math.abs(converted.widthMm-widthMm)>.05||Math.abs(converted.heightMm-heightMm)>.05))
                throw new IOException("Layer artboards disagree; DXF layer alignment would be unsafe");
            widthMm=converted.widthMm;heightMm=converted.heightMm;
            openPaths+=converted.openPaths;closedContours+=converted.closedContours;vertices+=converted.vertices;
            geometrySizes.append(String.format(Locale.US,
                "%s: geometry bounds x %.3f..%.3f mm; y %.3f..%.3f mm; geometry size %.3f x %.3f mm; closed contours %d; open drawing paths %d\n",
                name,converted.minXmm,converted.maxXmm,converted.minYmm,converted.maxYmm,
                converted.maxXmm-converted.minXmm,converted.maxYmm-converted.minYmm,
                converted.closedContours,converted.openPaths));
            putText(name,converted.dxf);
        }else{
            putText(name,svg);
            retainForCombined(name,svg);
        }
        sheets++;return name;
    }
    void finish() throws IOException {
        if(sheets<1)throw new IOException("No physical cut sheets exported");
        // A previously unseen final-sheet collision must NOT become a silent
        // publish; the user must have approved cut-geometry review first.
        if(criticalSheetCount()>0&&!reviewApproved)
            throw new IOException("Final cut sheets contain intersecting or invalid contours. " +
                "No file was published. Review artwork and reduce overlapping or narrow paths.");
        // Narrow contours are no more cut-qualified than invalid contacts.
        // The caller must explicitly request an inspection-only ZIP before
        // a warning-bearing specialty sheet is written.
        if(!reviewApproved && narrowFindingCount()>0){
            String firstNarrowSheet="unknown";
            for(FinalCutSheetSafety.Report report:physicalReports)if(report.narrow>0){
                firstNarrowSheet=report.sheet;break;
            }
            throw new FinalCutReviewRequired("Final cut sheet contains undersized or closely spaced contours. " +
                "Use inspection-only export after reviewing the warnings: " + firstNarrowSheet);
        }
        // A budget-exhausted or unsupported FINAL sheet is not an all-clear.
        // Preserve the legacy mixed-pen/blade handoff: those geometries are
        // inspected individually but flagged for distinct tools. The advisory
        // alone does not mean that the physical intersection scan was aborted.
        // All other UNVERIFIED findings require explicit user review.
        if(!reviewApproved)for(FinalCutSheetSafety.Report report:physicalReports){
            boolean mixedToolNoticeOnly=report.unverified==1 &&
                report.detail.contains("Mixed blade and open drawing paths") &&
                !report.detail.contains("incomplete") &&
                !report.detail.contains("exceeds detailed geometry inspection budget") &&
                !report.detail.contains("could not be fully parsed");
            if(report.unverified>0&&!mixedToolNoticeOnly)
                throw new FinalCutReviewRequired("Final cut sheet inspection is incomplete. " +
                    "Reduce detail or explicitly review unverified cutting geometry before export: "+report.sheet);
        }
        writeCombinedSvg();
        StringBuilder check=new StringBuilder("sheet,status,closed_contours,circles,open_paths,contacts,narrow,unverified,details\n");
        for(FinalCutSheetSafety.Report report:physicalReports)check.append(report.csv());
        putText("cut-sheet-safety.csv",check.toString());
        putText("physical-artboards.csv",artboards.toString());
        if(outputDeviceId==OutputDeviceProfile.CRICUT)putText(CricutMatPreflight.NOTE_FILE,CricutMatPreflight.note(widthMm,heightMm,selectedMat));
        putText("cut-complexity.csv",complexity.toString());
        // An explicitly acknowledged unverified ZIP can be shared for
        // technical diagnosis, but must never be presented as cut-qualified.
        if(reviewApproved&&(sourceFindings||reviewSheetCount()>0))
            putText("NOT-CUT-QUALIFIED.txt",
                "WARNING: CUT GEOMETRY HAS NOT BEEN QUALIFIED.\n"+
                "Some contours were NOT FULLY INSPECTED, underwidth, or contacted other cuts.\n"+
                "This ZIP was exported for inspection after cut-safety warnings\n"+
                "cut-safety checks. Do not manufacture from it until contours,\n"+
                "dimensions, minimum clearances and registration have been\n"+
                "independently verified. See cut-sheet-safety.csv.\n");
        if(isDxf()){
            String info=String.format(Locale.US,
                "VinylStudio DXF R13 / Silhouette Basic Edition\n"+
                "Original SVG artboard (REFERENCE ONLY): %.3f x %.3f mm (%.4f x %.4f in)\n"+
                "Cut sheet count: %d\nClosed contours: %d\nOpen drawing paths: %d\nPolyline vertices: %d\n"+
                "Each separate DXF is exported using a common millimeter artboard and origin.\n"+
                "The DXF file may IMPORT AT A DIFFERENT SCALE in Silhouette Studio.\n"+
                "IMPORTANT: Silhouette may trim the empty artboard around the shapes.\n"+
                "Do NOT set the selection bounding box to the artboard width/height above.\n"+
                "Verify/restore EACH file's ACTUAL GEOMETRY size from the measurements below.\n"+
                "For multilayer alignment, preserve common registration marks and positions.\n"+
                "When registration marks are off, reliable separate-file alignment is not guaranteed.\n"+
                "Registration marks represent physical vinyl alignment, NOT optical Print & Cut marks.\n"+
                "Open centerlines require pen/engraving tooling; they are not weedable vinyl ribbons.\n"+
                "No machine is controlled. Check blade settings and scrap-test before cutting.\n",
                widthMm,heightMm,widthMm/25.4,heightMm/25.4,sheets,closedContours,openPaths,vertices);
            putText("dimensions-dxf.txt",info+"\nPER-FILE PHYSICAL CUT GEOMETRY BOUNDS:\n"+geometrySizes);
        }
    }
    private void retainForCombined(String name,String svg){
        if(combinedSkipReason!=null)return;
        combinedChars+=svg.length();
        if(combinedChars>MAX_COMBINED_CHARS){
            combinedSheets.clear();
            combinedSkipReason="The layers are too large to combine into one file on this device.";
            return;
        }
        combinedSheets.add(new CombinedLayerSvg.Sheet(name,svg));
    }
    /** One SVG with every layer, so Design Space needs a single upload. Never blocks the
     *  export: when the combined file would break the cutter's limits, a note explains
     *  why and the separate sheets remain the hand-off. */
    private void writeCombinedSvg() throws IOException {
        if(isDxf()||sheets<2)return;
        String combined=null;
        if(combinedSkipReason==null){
            try{
                combined=CombinedLayerSvg.merge(combinedSheets);
                CutSvgCompatibility.verify(combined,outputDeviceId);
                // The single upload must be the SAME physical artboard the sheets proved, and on
                // Cricut it is held to the selected mat too. Fail closed; never skip silently.
                SvgDxfR13Exporter.SafetyGeometry whole=SvgDxfR13Exporter.safetyGeometry(combined);
                if(!(whole.widthMm>0&&whole.heightMm>0)||Math.abs(whole.widthMm-widthMm)>.05||Math.abs(whole.heightMm-heightMm)>.05)
                    throw new IOException("Single-upload SVG artboard differs from the layer sheets; not published");
                if(outputDeviceId==OutputDeviceProfile.CRICUT)
                    CricutMatPreflight.requireFits(whole.widthMm,whole.heightMm,CombinedLayerSvg.FILENAME,selectedMat);
            }catch(IllegalArgumentException tooBig){
                combined=null;
                combinedSkipReason=tooBig.getMessage();
            }
        }
        combinedSheets.clear();
        if(combined!=null)putText(CombinedLayerSvg.FILENAME,combined);
        else putText(CombinedLayerSvg.SKIPPED_NOTE,
            "No single-upload SVG was made for this export.\n"+combinedSkipReason+"\n"+
            "Upload the separate layer SVGs instead, or lower Level of detail and export again.\n");
    }
    private void putText(String name,String content)throws IOException{
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
}

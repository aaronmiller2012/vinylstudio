package com.halftonestudio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Export sheet contract (ADR-0001), exercised through REAL engines and the real ZIP writer.
 *
 * For every export family that the app can pair together, and for standard and circle crops
 * at 3 in and 11 in:
 *  1. Every sheet's fill is its layer swatch, and the combined one-upload SVG keeps one
 *     distinct color per sheet in physical (put) order.
 *  2. Every sheet of one export shares one coordinate frame: CutSheetZipWriter accepts all of
 *     them, and the four registration marks parsed from each SVG sit at identical board
 *     coordinates.
 *  3. Exported marks equal the marks the editor preview draws
 *     (RegistrationMarks.automaticPositions, the source used by CanvasView.displayedMarks).
 *
 * Mark centers are recovered by parsing the emitted SVG (SvgDxfR13Exporter.safetyGeometry),
 * so translate groups and viewBox origins are honored exactly as a cutter importer would.
 */
public final class V3ExportSheetContractTest {
    static final float MARK_MM=7.3f, CLEAR_MM=1f, MIN_CUT=.254f;
    static final float[] SIZES={76.2f,266.7f}; // 3 in and 10.5 in: the largest portrait width whose marked sheet fits a 12 in mat
    static final List<String> failures=new ArrayList<>();
    static int checks;

    static void check(boolean ok,String message){checks++;if(!ok)failures.add(message);}

    /** Portrait fixture; with circle crop, pixels outside the inscribed ellipse are transparent
     *  exactly as SpecialtyCutSafety/Snip preparation leaves them. */
    static int[] photo(int w,int h,boolean circle){
        int[] p=V3AllStyleExportMatrixTest.portrait(w,h);
        if(circle)for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double nx=(x+.5-w*.5)/(w*.5),ny=(y+.5-h*.5)/(h*.5);
            if(nx*nx+ny*ny>.92)p[y*w+x]=0;
        }
        return p;
    }
    static SnipMask keepCenter(){
        SnipMask m=new SnipMask();
        m.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,.12f,.12f,.88f,.12f,.88f,.88f,.12f,.88f));
        return m;
    }
    static VinylEngine.Settings standardSettings(float widthMm,boolean circle,boolean snip){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=widthMm;s.minimumMm=MIN_CUT;s.registration=true;
        s.markStyle=RegistrationMarks.CIRCLE;s.markSizeMm=MARK_MM;s.registrationClearanceMm=CLEAR_MM;
        s.circleCrop=circle;s.outerSpacingMm=8f;
        if(snip)s.snips=keepCenter();
        return s;
    }

    /** Board-coordinate centers of the registration circles in one sheet, sorted. */
    static List<double[]> markCenters(String svg) throws Exception {
        SvgDxfR13Exporter.SafetyGeometry g=SvgDxfR13Exporter.safetyGeometry(svg);
        List<double[]> out=new ArrayList<>();
        // The parser returns cutter coordinates (origin bottom-left, Y up); convert to SVG board space.
        for(double[] c:g.circles)if(Math.abs(c[2]-MARK_MM/2)<.002)out.add(new double[]{c[0],g.heightMm-c[1]});
        out.sort((a,b)->a[1]!=b[1]?Double.compare(a[1],b[1]):Double.compare(a[0],b[0]));
        return out;
    }
    static double boardWidth(String svg) throws Exception {return SvgDxfR13Exporter.safetyGeometry(svg).widthMm;}

    static Set<String> fills(String svg){
        Set<String> out=new LinkedHashSet<>();
        Matcher m=Pattern.compile("fill=\"(#[0-9A-Fa-f]{6})\"").matcher(svg);
        while(m.find())out.add(m.group(1).toUpperCase(Locale.ROOT));
        return out;
    }
    static String hex(int argb){return String.format(Locale.US,"#%06X",argb&0xffffff);}

    /** One export: named sheets in physical order with the swatch each must carry. */
    static final class Export {
        final String label;final float artW,artH;final boolean circle;
        final Map<String,String> sheets=new LinkedHashMap<>();
        final Map<String,String> swatch=new LinkedHashMap<>();
        Export(String label,float w,float h,boolean circle){this.label=label;artW=w;artH=h;this.circle=circle;}
        void add(String name,String svg,int argb){sheets.put(name,svg);swatch.put(name,hex(argb));}
    }

    static void verify(Export e) throws Exception {verify(e,null);}
    static void verify(Export e,float[][] custom) throws Exception {
        String tag=e.label+String.format(Locale.US," @%.1fmm%s",e.artW,e.circle?" circle":"");
        check(e.sheets.size()>=2,tag+": fixture produced fewer than 2 sheets");
        // 1. Per-sheet color.
        for(Map.Entry<String,String> s:e.sheets.entrySet()){
            Set<String> f=fills(s.getValue());
            String want=e.swatch.get(s.getKey());
            check(f.size()==1&&f.contains(want),tag+": "+s.getKey()+" fills "+f+" but swatch is "+want);
        }
        // 2. Shared frame: the real writer must accept every sheet of the export.
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        String combined=null;
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            CutSheetZipWriter w=new CutSheetZipWriter(zip,OutputDeviceProfile.CRICUT,MIN_CUT,true);
            for(Map.Entry<String,String> s:e.sheets.entrySet())w.put(s.getKey(),s.getValue());
            w.finish();
        }catch(Exception rejected){
            check(false,tag+": ZIP writer rejected the export: "+rejected.getMessage());
        }
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
            for(ZipEntry z;(z=in.getNextEntry())!=null;)
                if(z.getName().equals(CombinedLayerSvg.FILENAME))combined=new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }catch(Exception unreadable){/* rejected above */}
        if(combined!=null){
            int at=-1;
            for(Map.Entry<String,String> s:e.swatch.entrySet()){
                int next=combined.indexOf("fill=\""+s.getValue()+"\"",at+1);
                check(next>at,tag+": combined SVG lost or reordered "+s.getKey()+" ("+s.getValue()+")");
                if(next>at)at=next;
            }
            check(fills(combined).size()==new LinkedHashSet<>(e.swatch.values()).size(),
                tag+": combined SVG has "+fills(combined).size()+" fills for "+new LinkedHashSet<>(e.swatch.values()).size()+" swatches");
        }
        // 2b + 3. Identical mark board coordinates on every sheet, equal to the preview.
        float[][] preview=custom!=null?custom:RegistrationMarks.automaticPositions(e.artW,e.artH,MARK_MM,CLEAR_MM,e.circle);
        List<double[]> reference=null;String referenceName=null;
        for(Map.Entry<String,String> s:e.sheets.entrySet()){
            List<double[]> marks=markCenters(s.getValue());
            check(marks.size()==4,tag+": "+s.getKey()+" has "+marks.size()+" registration marks");
            if(marks.size()!=4)continue;
            double pad=(boardWidth(s.getValue())-e.artW)/2;
            List<double[]> expected=new ArrayList<>();
            for(float[] p:preview)expected.add(new double[]{p[0]+pad,p[1]+pad});
            expected.sort((a,b)->a[1]!=b[1]?Double.compare(a[1],b[1]):Double.compare(a[0],b[0]));
            for(int i=0;i<4;i++){
                double d=Math.hypot(marks.get(i)[0]-expected.get(i)[0],marks.get(i)[1]-expected.get(i)[1]);
                check(d<.01,String.format(Locale.US,"%s: %s mark %d at (%.3f,%.3f), preview places it at (%.3f,%.3f)",
                    tag,s.getKey(),i,marks.get(i)[0]-pad,marks.get(i)[1]-pad,expected.get(i)[0]-pad,expected.get(i)[1]-pad));
            }
            if(reference==null){reference=marks;referenceName=s.getKey();continue;}
            for(int i=0;i<4;i++){
                double d=Math.hypot(marks.get(i)[0]-reference.get(i)[0],marks.get(i)[1]-reference.get(i)[1]);
                check(d<.01,String.format(Locale.US,"%s: %s mark %d is %.3f mm from %s",tag,s.getKey(),i,d,referenceName));
            }
        }
    }

    // ---- export families, assembled the way StudioActivity assembles them ----

    static Export standard(float size,boolean circle,boolean base) throws Exception {
        int w=60,h=72;int[] px=photo(w,h,circle);
        VinylEngine.Settings s=standardSettings(size,circle,base);
        VinylEngine.Result r=VinylEngine.generate(px,w,h,s);
        Export e=new Export("standard CMYK"+(base?" + Snip base":""),size,r.heightMm,circle);
        if(base){
            AutoSilhouetteUnderlay.Output b=AutoSilhouetteUnderlay.make(s,px,w,h,0xff2a6f97);
            check(b!=null,"standard base not generated");
            if(b!=null)e.add("01-solid-snip.svg",b.svg(),b.color);
        }
        int sheet=base?2:1;
        for(int i=0;i<r.settings.layers.size();i++){
            if(!VinylEngine.cutLayer(r,i))continue;
            e.add(String.format(Locale.US,"%02d-vinyl.svg",sheet++),VinylEngine.svg(r,i),r.settings.layers.get(i).color);
        }
        return e;
    }

    /** Woven Ribbon: 4 colors, Snip-base eligible. Linocut: single ink, the base is its second sheet. */
    static Export suite(int style,float size,boolean circle,boolean base) throws Exception {
        int w=60,h=72;int[] px=photo(w,h,circle);
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        c.style=style;c.widthMm=size;c.detail=25;c.colorCount=4;c.minimumMm=MIN_CUT;c.draft=false;c.weightMm=.7f;
        StyleKit.Result r=VinylStyleSuite.generate(px,w,h,c);
        Export e=new Export("suite "+VinylStyleSuite.NAMES[style]+(base?" + Snip base":""),size,r.heightMm,circle);
        if(base){
            // StudioActivity.makeNewStyleUnderlay: 8 mm margin with registration.
            VinylEngine.Settings s=standardSettings(size,circle,true);s.outerSpacingMm=8f;
            AutoSilhouetteUnderlay.Output b=AutoSilhouetteUnderlay.make(s,px,w,h,0xffefe6d2);
            check(b!=null,"suite base not generated");
            if(b!=null)e.add("01-solid-snip.svg",b.svg(),b.color);
        }
        int sheet=base?2:1;
        for(int i=0;i<r.layers.size();i++){
            StyleKit.Layer l=r.layers.get(i);
            if(l.loops.isEmpty()&&l.lines.isEmpty())continue;
            e.add(String.format(Locale.US,"L%02d-suite.svg",sheet++),
                SuiteExportCall.layerSvg(r,i,circle),l.color);
        }
        return e;
    }

    static Export tessellation(float size,boolean circle,boolean base) throws Exception {
        int w=60,h=72;int[] px=photo(w,h,circle);
        TessellationEngine.Options o=new TessellationEngine.Options();
        o.style=TessellationEngine.VORONOI;o.layerCount=3;o.widthMm=size;o.detail=30;o.registration=true;
        o.markStyle=RegistrationMarks.CIRCLE;o.markSizeMm=MARK_MM;o.registrationClearanceMm=CLEAR_MM;o.minimumCutMm=MIN_CUT;
        o.outerSpacingMm=8f;
        SuiteExportCall.circle(o,circle);
        if(circle){o.allowedPixels=new boolean[w*h];for(int i=0;i<w*h;i++)o.allowedPixels[i]=(px[i]>>>24)>=128;}
        TessellationEngine.Result r=TessellationEngine.generate(px,w,h,o);
        Export e=new Export("tessellation Voronoi"+(base?" + Snip base":""),size,size*h/(float)w,circle);
        if(base){
            VinylEngine.Settings s=standardSettings(size,circle,true);
            AutoSilhouetteUnderlay.Output b=AutoSilhouetteUnderlay.make(s,px,w,h,0xff3b3b3b);
            check(b!=null,"tessellation base not generated");
            if(b!=null)e.add("01-solid-snip.svg",b.svg(),b.color);
        }
        int sheet=base?2:1;
        for(int i=0;i<o.layerCount;i++){
            if(r.counts[i]==0)continue;
            e.add(String.format(Locale.US,"%02d-tessellation.svg",sheet++),r.svg(i),o.colors[i]);
        }
        return e;
    }

    /** Layered cells and multilayer stencils never pair with a Snip base sheet in the app. */
    static Export cells(float size,boolean circle) throws Exception {
        int w=60,h=72;int[] px=photo(w,h,circle);
        LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
        o.layerCount=3;o.widthMm=size;o.registration=true;o.markStyle=RegistrationMarks.CIRCLE;o.markSizeMm=MARK_MM;
        o.registrationClearanceMm=CLEAR_MM;o.minimumCutMm=MIN_CUT;o.outerSpacingMm=8f;o.circleCrop=circle;
        if(circle){o.allowedPixels=new boolean[w*h];for(int i=0;i<w*h;i++)o.allowedPixels[i]=(px[i]>>>24)>=128;}
        LayeredCellPortraitEngine.Result r=LayeredCellPortraitEngine.generate(px,w,h,o);
        Export e=new Export("layered cells",size,size*h/(float)w,circle);
        for(int i=0;i<o.layerCount;i++){
            if(r.pieceCounts[i]==0)continue;
            e.add(String.format(Locale.US,"%02d-cells.svg",i+1),r.svg(i),r.options.colors[i]);
        }
        return e;
    }
    static Export stencil(float size,boolean circle) throws Exception {
        int w=60,h=72;int[] px=photo(w,h,circle);
        MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
        o.sourceColorMode=true;o.layerCount=3;o.widthMm=size;o.registration=true;o.markStyle=RegistrationMarks.CIRCLE;
        o.markDiameterMm=MARK_MM;o.registrationClearanceMm=CLEAR_MM;o.minimumCutMm=MIN_CUT;o.outerSpacingMm=8f;o.circleCrop=circle;
        MultilayerStencilEngine.Result r=MultilayerStencilEngine.generate(px,w,h,o);
        Export e=new Export("multilayer stencil",size,r.heightMm,circle);
        for(int i=0;i<r.layers.size();i++){
            String svg;
            try{svg=r.svg(i);}catch(IllegalArgumentException empty){continue;}
            if(markCenters(svg).isEmpty())continue;
            e.add(String.format(Locale.US,"%02d-stencil.svg",i+1),svg,r.layers.get(i).color);
        }
        return e;
    }

    /** Asymmetric user-placed marks: catches flipped or shifted marks that symmetric defaults hide. */
    static float[][] customMarks(float w,float h){
        return new float[][]{{-6f,-9f},{w+7f,-5f},{-8f,h+6f},{w*.5f,h+11f}};
    }
    static void customMarkCase() throws Exception {
        int w=60,h=72;int[] px=photo(w,h,false);
        float size=101.6f;
        VinylEngine.Settings s=standardSettings(size,false,true);
        float hh=size*h/w;s.marks=customMarks(size,hh);
        VinylEngine.Result r=VinylEngine.generate(px,w,h,s);
        Export e=new Export("standard + base, custom asymmetric marks",size,r.heightMm,false);
        AutoSilhouetteUnderlay.Output b=AutoSilhouetteUnderlay.make(s,px,w,h,0xff2a6f97);
        e.add("01-solid-snip.svg",b.svg(),b.color);
        int sheet=2;
        for(int i=0;i<r.settings.layers.size();i++)if(VinylEngine.cutLayer(r,i))
            e.add(String.format(Locale.US,"%02d-vinyl.svg",sheet++),VinylEngine.svg(r,i),r.settings.layers.get(i).color);
        verify(e,s.marks);
    }

    public static void main(String[] args) throws Exception {
        try{customMarkCase();}catch(Exception|AssertionError crash){check(false,"custom marks: "+crash);}
        for(float size:SIZES)for(boolean circle:new boolean[]{false,true})for(boolean base:new boolean[]{false,true}){
            for(String family:new String[]{"standard","suite","linocut","tessellation","cells","stencil"}){
                if(family.equals("linocut")&&!base)continue; // single ink: one sheet without a base
                if((family.equals("cells")||family.equals("stencil"))&&base)continue; // no Snip base in the app
                try{
                    Export e=family.equals("standard")?standard(size,circle,base):
                        family.equals("suite")?suite(VinylStyleSuite.WOVEN_RIBBON,size,circle,base):
                        family.equals("linocut")?suite(VinylStyleSuite.LINOCUT,size,circle,base):
                        family.equals("cells")?cells(size,circle):family.equals("stencil")?stencil(size,circle):
                        tessellation(size,circle,base);
                    verify(e);
                }catch(Exception|AssertionError crash){
                    check(false,String.format(Locale.US,"%s base=%s circle=%s @%.1f: %s: %s",family,base,circle,size,
                        crash.getClass().getSimpleName(),crash.getMessage()));
                }
            }
        }
        if(!failures.isEmpty()){
            System.out.println("FAIL export sheet contract: "+failures.size()+" of "+checks+" checks");
            for(String f:failures)System.out.println("  - "+f);
            throw new AssertionError(failures.size()+" export sheet contract failures");
        }
        System.out.println("PASS export sheet contract: "+checks+" checks over standard, suite, tessellation, cell and stencil exports"+
            " (with/without Snip base, standard/circle crop, 3 in/10.5 in)");
    }
}

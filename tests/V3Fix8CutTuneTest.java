package com.halftonestudio;

import java.util.*;

/**
 * Fix8 cut tuning: export inspection separates kept vinyl from weeded waste,
 * export-time conditioning repairs organic sheets, and per-style generator
 * fixes produce sheets the export inspector accepts at the app defaults
 * (0.8 mm minimum cut width, 0.5 mm minimum gap). Host-only, no Android APIs.
 */
public final class V3Fix8CutTuneTest {
    static float[] rect(float x0,float y0,float x1,float y1){return new float[]{x0,y0,x1,y0,x1,y1,x0,y1};}
    static List<StyleCutGeometryPreflight.Warning> inspect(float min,float gap,float[]... loops){
        StyleKit.Result r=new StyleKit.Result(100,100);StyleKit.Layer l=new StyleKit.Layer("t",0);
        l.loops.addAll(Arrays.asList(loops));r.layers.add(l);
        return gap>0?StyleCutGeometryPreflight.inspect(r,min,gap,50):StyleCutGeometryPreflight.inspect(r,min,50);
    }
    static int[] portrait(int w,int h){
        int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double fx=(x-w*.5)/(w*.5),fy=(y-h*.45)/(h*.5);
            double v=.8-.3*y/h;
            if(fx*fx/.42+fy*fy/.62<1)v=.65-.2*fx+.1*Math.cos(fy*6)+.08*Math.sin(x*.3)*Math.cos(y*.27);
            for(int s=-1;s<=1;s+=2){double ex=fx-s*.24,ey=fy+.12;if(ex*ex/.012+ey*ey/.004<1)v=.12;}
            int k=(int)Math.max(2,Math.min(253,255*v));
            p[y*w+x]=0xff000000|(Math.min(255,k+18)<<16)|(k<<8)|Math.max(0,k-22);
        }
        return p;
    }
    public static void main(String[] args)throws Exception{
        // 1. Weed gap vs vinyl width. Two 5 mm squares 0.6 mm apart: a legal
        //    0.5 mm weed gap, but the legacy single threshold flagged it.
        float[] a=rect(10,10,15,15),b=rect(15.6f,10,20.6f,15);
        if(inspect(.8f,-1,a,b).isEmpty())throw new AssertionError("legacy strict mode must still flag 0.6 mm at 0.8 mm");
        if(!inspect(.8f,.5f,a,b).isEmpty())throw new AssertionError("0.6 mm weed gap must pass a 0.5 mm gap setting");
        float[] c=rect(15.3f,10,20.3f,15);
        if(inspect(.8f,.5f,a,c).isEmpty())throw new AssertionError("0.3 mm weed gap must be flagged");
        // A 0.6 mm vinyl neck between two lobes is still flagged at 0.8 mm.
        float[] neck={10,10,14,10,14,11.7f,16,11.7f,16,10,20,10,20,14,16,14,16,12.3f,14,12.3f,14,14,10,14};
        if(inspect(.8f,.5f,neck).isEmpty())throw new AssertionError("0.6 mm vinyl neck must be flagged");
        // Rounded ribbon end (0.85 mm wide) and a 60-degree tip are not necks.
        float[] ribbon=new float[]{10,20,30,20,30.3f,20.1f,30.42f,20.425f,30.3f,20.75f,30,20.85f,10,20.85f};
        if(!inspect(.8f,.5f,ribbon).isEmpty())throw new AssertionError("0.85 mm ribbon with a rounded end must pass");
        float[] tri={50,50,60,50,55,58.66f};
        if(!inspect(.8f,.5f,tri).isEmpty())throw new AssertionError("equilateral triangle tips are not vinyl necks");
        // The selected weed gap is independent of minimum kept-vinyl width.
        if(StyleCutGeometryPreflight.exportGapMm(.5f,.254f)!=.5f)throw new AssertionError("requested gap must not be lowered to minimum width");
        if(StyleCutGeometryPreflight.exportGapMm(0f,.8f)!=.25f)throw new AssertionError("gap floor must be 0.25 mm");
        // 2. Conditioner removes a sub-minimum neck, specks and a pinhole, and
        //    its result passes the classified inspection.
        ArrayList<float[]> loops=new ArrayList<>();
        loops.add(new float[]{10,10,14,10,14,11.85f,16,11.85f,16,10,20,10,20,14,16,14,16,12.15f,14,12.15f,14,14,10,14});
        loops.add(rect(20.2f,10,24,14));
        loops.add(rect(30,30,30.3f,30.3f));
        loops.add(rect(40,40,50,50));loops.add(rect(44,44,44.3f,44.3f));
        ArrayList<float[]> fixed=CutGeometryConditioner.condition(loops,60,60,.8f,.5f);
        StyleKit.Result fr=new StyleKit.Result(60,60);StyleKit.Layer fl=new StyleKit.Layer("c",0);fl.loops.addAll(fixed);fr.layers.add(fl);
        List<StyleCutGeometryPreflight.Warning> left=StyleCutGeometryPreflight.inspect(fr,.8f,.5f,50);
        if(!left.isEmpty())throw new AssertionError("conditioned sheet still has findings: "+left.get(0).reason);
        if(fixed.size()!=4)throw new AssertionError("expected 4 pieces after conditioning, got "+fixed.size());
        // 3. 5x7 font: no glyph may touch itself at a corner pixel.
        PixelFont5x7 font=new PixelFont5x7();
        for(int cp=33;cp<127;cp++){
            ArrayList<float[]> glyph=font.outline(cp);if(glyph==null||glyph.isEmpty())continue;
            float[][] scaled=new float[glyph.size()][];
            for(int i=0;i<scaled.length;i++){scaled[i]=glyph.get(i).clone();for(int j=0;j<scaled[i].length;j++)scaled[i][j]*=7f;}
            for(StyleCutGeometryPreflight.Warning w:inspect(.99f,.99f,scaled))
                if(w.reason.startsWith("Touch")||w.reason.startsWith("Inter")||w.reason.startsWith("Overl"))
                    throw new AssertionError("glyph '"+(char)cp+"' has a corner contact");
        }
        // 4. ASCII screen: '\' has real width (was a zero-width sliver).
        java.lang.reflect.Method simple=AsciiScreenMotif.class.getDeclaredMethod("simpleShape",char.class,float.class,float.class,float.class,float.class);
        simple.setAccessible(true);
        for(char ch:new char[]{'/','\\'}){
            VinylEngine.Shape s=(VinylEngine.Shape)simple.invoke(null,ch,10f,10f,2f,.8f);
            if(Math.abs(StyleKit.signedArea(s.xy))<.5)throw new AssertionError("ASCII '"+ch+"' collapsed");
        }
        // 5. Fish scales: no self-crossing outlines.
        int W=180,H=225;int[] px=portrait(W,H);
        LayeredCellPortraitEngine.Options cells=new LayeredCellPortraitEngine.Options();
        cells.columns=LayeredCellReleaseProfile.safeColumns(26,W,H,152.4f,.85f,.56f,false);
        cells.layerCount=5;cells.widthMm=152.4f;cells.gapMm=.56f;cells.minFeatureMm=.85f;cells.minimumCutMm=.8f;
        LayeredCellPortraitEngine.Result scales=FishScaleMosaicEngine.generate(px,W,H,cells);
        for(int layer=0;layer<scales.options.layerCount;layer++){
            if(scales.pieceCounts[layer]==0)continue;
            for(float[] loop:SvgDxfR13Exporter.safetyGeometry(scales.svg(layer)).closedLoops)
                if(StyleKit.selfIntersections(loop,1)>0)throw new AssertionError("fish scale outline crosses itself");
        }
        // 6. Native generator output passes export inspection at app defaults.
        boolean[] allowed=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){float mx=(x+.5f)*152.4f/W,my=(y+.5f)*152.4f*H/W/H;allowed[y*W+x]=mx>=5&&my>=5&&mx<=147.4f&&my<=152.4f*H/W-5;}
        int[] swatches=new int[9];System.arraycopy(VinylEngine.START_COLORS,0,swatches,0,8);swatches[8]=0xff777777;
        for(int pattern:new int[]{0,2,TessellationMotifEngine.CROSSHAIR_GRID}){
            VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();
            cfg.style=VinylStyleSuite.TESSELLATION_MOTIF;cfg.variant=pattern;cfg.detail=55;cfg.colorCount=5;
            cfg.widthMm=152.4f;cfg.minimumMm=.8f;cfg.gapMm=.5f;cfg.allowed=allowed.clone();
            cfg.palette=NewStyleColorSlots.fixedPalette(cfg.style,pattern,5,swatches);
            StyleKit.Result r=VinylStyleSuite.generate(px,W,H,cfg);
            for(int i=0;i<r.layers.size();i++){
                FinalCutSheetSafety.Report rep=FinalCutSheetSafety.inspect("t",NewStyleExport.layerSvg(r,i,true,0,2.5f,1f,null,.8f),.8f,.5f);
                if(rep.needsReview())throw new AssertionError("tessellation "+pattern+" sheet "+i+": "+rep.detail);
            }
        }
        TessellationEngine.Options mosaic=new TessellationEngine.Options();
        mosaic.style=TessellationEngine.VORONOI;mosaic.widthMm=152.4f;mosaic.gapMm=.56f;mosaic.minimumCutMm=.8f;
        TessellationEngine.Result voronoi=TessellationEngine.generate(px,W,H,mosaic);
        for(int i=0;i<voronoi.counts.length;i++){
            if(voronoi.counts[i]==0)continue;
            FinalCutSheetSafety.Report rep=FinalCutSheetSafety.inspect("v",voronoi.svg(i),.8f,.5f);
            if(rep.needsReview())throw new AssertionError("Voronoi sheet "+i+": "+rep.detail);
        }
        // 7. Export preparation never edits the preview Result.
        VinylStyleSuite.Config org=new VinylStyleSuite.Config();
        org.style=VinylStyleSuite.CONFETTI_ROUND;org.detail=55;org.colorCount=4;org.widthMm=152.4f;org.minimumMm=.8f;org.gapMm=.5f;org.allowed=allowed.clone();
        StyleKit.Result preview=VinylStyleSuite.generate(px,W,H,org);
        int before=0;for(StyleKit.Layer l:preview.layers)before+=l.loops.size();
        StyleKit.Result cut=VinylStyleSuite.prepareForCutting(preview,org.style,.8f,.5f);
        int after=0;for(StyleKit.Layer l:preview.layers)after+=l.loops.size();
        if(before!=after||cut==preview)throw new AssertionError("prepareForCutting must work on a copy");
        for(int i=0;i<cut.layers.size();i++){
            FinalCutSheetSafety.Report rep=FinalCutSheetSafety.inspect("o",NewStyleExport.layerSvg(cut,i,true,0,2.5f,1f,null,.8f),.8f,.5f);
            if(rep.critical>0)throw new AssertionError("prepared organic sheet has contact findings: "+rep.detail);
        }
        System.out.println("PASS Fix8 vinyl/waste inspection, conditioning, glyph, fish-scale, mosaic and tessellation cut tuning");
    }
}

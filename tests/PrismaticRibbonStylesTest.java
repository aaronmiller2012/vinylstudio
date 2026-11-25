package com.halftonestudio;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipOutputStream;

/**
 * Prismatic ribbon milestone: the five appended styles (IDs 48-52), their registry and
 * policy wiring, the kit's geometry helper, and REAL engine output through the
 * production JTS vector backend (final, post-Boolean, post-conditioning geometry).
 * Also pins Prismatic Diagonal's vector output to the 559 baseline signature.
 */
public final class PrismaticRibbonStylesTest {
    static int checks=0;
    static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    static final int[] RIBBONS={VinylStyleSuite.PRISMATIC_CONTOUR_FLOW,VinylStyleSuite.PRISMATIC_TWILL,
        VinylStyleSuite.PRISMATIC_SILK,VinylStyleSuite.PRISMATIC_BRAIDED_FLOW,VinylStyleSuite.PRISMATIC_CHEVRON};
    static final int[] PALETTE={0xfff4ece0,0xffd9a07a,0xffb0583c,0xff6a7fa8,0xff3b3550,0xff1c1a20,0xff8fb39a,0xffe2c15a};

    public static void main(String[] args)throws Exception{
        VectorBooleanEngine.install(new JtsVectorBooleanBackend());
        registry();
        geometryHelper();
        diagonalUnchanged();
        engine();
        System.out.println("PASS prismatic ribbon styles: "+checks+" checks");
    }

    // ---------------------------------------------------------------- registry
    static void registry(){
        check(VinylStyleSuite.NAMES.length==53,"53 style identities");
        check(VinylStyleSuite.POSTER_STENCIL==47&&VinylStyleSuite.PRISMATIC_DIAGONAL==26,"historical IDs unchanged");
        check("Prismatic Diagonal Portrait".equals(VinylStyleSuite.NAMES[26]),"Diagonal name unchanged");
        String[] names={"Prismatic Contour Flow","Prismatic Twill","Prismatic Silk","Prismatic Braided Flow","Prismatic Chevron"};
        PrismaticRibbonPortraitEngine.Mode[] modes=PrismaticRibbonPortraitEngine.Mode.values();
        HashSet<String> keys=new HashSet<>();
        for(int i=0;i<RIBBONS.length;i++){
            int id=RIBBONS[i];
            check(id==48+i,"appended ID "+id);
            check(names[i].equals(VinylStyleSuite.NAMES[id]),"name "+id);
            check(VinylStyleSuite.isPrismaticRibbon(id)&&VinylStyleSuite.isPrismaticFamily(id),"family "+id);
            check(VinylStyleSuite.ribbonMode(id)==modes[i],"explicit mode "+id);
            check(!VinylStyleSuite.isRetiredStyleId(id)&&VinylStyleSuite.restoredStyleId(id)==id,"saved ID restores "+id);
            check(NewStyleVariantPolicy.count(id)==1&&NewStyleVariantPolicy.selectable(id),"selectable "+id);
            check(NewStyleColorSlots.multi(id)&&NewStyleColorSlots.minimumSelectable(id)==2&&NewStyleColorSlots.maximumSelectable(id)==8,"2-8 layers "+id);
            check(NewStyleLayerPolicy.isStructuralLayer(id,0)&&!NewStyleLayerPolicy.isStructuralLayer(id,1),"L1 structural "+id);
            check(FoundationColorPolicy.newStylePolicy(id)==FoundationColorPolicy.STRUCTURAL&&FoundationColorPolicy.photoBaseIsFirst(id)
                &&FoundationColorPolicy.lightFoundation(id),"light foundation "+id);
            check(PaletteStylePolicy.familyForNewStyle(id)==PaletteStylePolicy.PRISMATIC_CELLS,"palette family "+id);
            String mode=modes[i].name().toLowerCase(Locale.ROOT);
            check(NewStyleColorSlots.slot(id,"prismatic-"+mode+"-4",0)==3,"slot from layer name "+id);
            String key=StudioThumbnailRegistry.newStyle(id);
            check(key.equals("multilayer.prismatic_"+mode),"thumbnail key "+key);
            check(StudioThumbnailRegistry.isKnown(key)&&keys.add(key),"unique thumbnail "+key);
            check(!Arrays.equals(StudioThumbnailPalette.colors(key),StudioThumbnailPalette.colors(StudioThumbnailRegistry.newStyle(26))),"own thumbnail palette "+key);
        }
        check(!VinylStyleSuite.isPrismaticRibbon(VinylStyleSuite.PRISMATIC_DIAGONAL),"Diagonal keeps its own dispatch");
        check(StudioThumbnailRegistry.allKeys().size()==81+28,"81 top-level + 28 tessellation thumbnails");
        boolean rejected=false;
        try{VinylStyleSuite.ribbonMode(VinylStyleSuite.PRISMATIC_DIAGONAL);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"ribbonMode rejects non-ribbon IDs");
        // Palette mapping anchors a light foundation and never reorders a reversed user stack.
        int[] eight=Arrays.copyOf(PALETTE,8);
        int[] mapped=VinylPaletteMapping.forStyle(RIBBONS[0],6,0xffffffff,eight,false,true);
        check(Arrays.equals(Arrays.copyOf(mapped,6),Arrays.copyOf(eight,6)),"photo palette order kept");
    }

    // ------------------------------------------------------------ kit helpers
    static void geometryHelper(){
        for(PrismaticRibbonGeometry.Pattern mode:PrismaticRibbonGeometry.Pattern.values())
            for(boolean cross:new boolean[]{false,true}){
                float prev=0;
                for(int i=-500;i<=500;i++){
                    float along=i*.025f,amp=.35f;
                    float wave=PrismaticRibbonGeometry.wave(mode,along,amp,1.8f,.7f,cross);
                    check(Float.isFinite(wave)&&Math.abs(wave)<=amp*1.56f,"wave bound "+mode);
                    if(i>-500)check(Math.abs(wave-prev)<.12f,"continuous wave "+mode);
                    prev=wave;
                    for(int lane=-3;lane<=3;lane++){
                        float a=PrismaticRibbonGeometry.decorativeOffset(mode,lane,along,1,cross);
                        float b=PrismaticRibbonGeometry.decorativeOffset(mode,lane+1,along,1,cross);
                        check(Math.abs(a)<=PrismaticRibbonGeometry.BRAID_OFFSET+1e-6f,"offset bound");
                        check(1+b-a>=1-2*PrismaticRibbonGeometry.BRAID_OFFSET-1e-5f,"lane ordering");
                    }
                }
            }
        check(PrismaticRibbonGeometry.tangentSlope(0,0,0,false)==0,"flat field");
        check(Math.abs(PrismaticRibbonGeometry.tangentSlope(1,0,1,true))<1e-6,"isotropic field");
        float[] offsets={-.12f,.12f,-.05f};
        for(int i=0;i<=200;i++)check(Math.abs(PrismaticRibbonGeometry.interpolate(offsets,0,3,i*.01f))<=.120001f,"interpolation bound");
        check(PrismaticRibbonGeometry.prior(PrismaticRibbonGeometry.Pattern.SILK,false,0,0,.5f,.6f,1)>
            PrismaticRibbonGeometry.prior(PrismaticRibbonGeometry.Pattern.SILK,false,0,0,.5f,.1f,1),"silk prior");
    }
    // ------------------------------------------------------------- engine
    static int[] portrait(int w,int h){return V3AllStyleExportMatrixTest.portrait(w,h);}
    static VinylStyleSuite.Config config(int style,float widthMm,int layers,int[] palette){
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();
        c.style=style;c.widthMm=widthMm;c.detail=55;c.colorCount=layers;c.minimumMm=.4f;
        c.gapMm=VinylStyleSuite.generationGapMm(.5f,.4f);c.paper=0xfffaf9f5;
        c.palette=palette==null?null:Arrays.copyOf(palette,layers);
        return c;
    }
    static String signature(StyleKit.Result r)throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");StringBuilder sb=new StringBuilder();
        for(StyleKit.Layer l:r.layers){
            double area=0;
            for(float[] p:l.loops){area+=StyleKit.signedArea(p);for(float v:p){int b=Float.floatToIntBits(v);md.update((byte)b);md.update((byte)(b>>8));md.update((byte)(b>>16));md.update((byte)(b>>24));}}
            sb.append(String.format(Locale.US,"%s:%d:%.2f;",l.name,l.loops.size(),Math.abs(area)));
        }
        byte[] d=md.digest();for(int i=0;i<6;i++)sb.append(String.format("%02x",d[i]));
        return sb.toString();
    }
    /** Recorded from build 559 (m4-559, 583bda8) with the JTS backend: Diagonal must not move. */
    static final String DIAGONAL_559="prismatic-diagonal-1:1:12903.14;prismatic-diagonal-2:32:3561.99;prismatic-diagonal-3:80:3775.54;"+
        "prismatic-diagonal-4:38:1155.32;prismatic-diagonal-5:43:4067.86;prismatic-diagonal-6:37:1819.40;7ce174374d46";
    /** The 559 engine is preserved: with conditioning off, the vector sheets are byte-identical
     *  to build 559. Production (M5) conditions the final sheets to the export weed gap; that
     *  output must keep the ribbon layout (same layers, similar area) and drop the findings. */
    static void diagonalUnchanged()throws Exception{
        VinylStyleSuite.Config c=config(VinylStyleSuite.PRISMATIC_DIAGONAL,101.6f,6,PALETTE);
        PrismaticDiagonalPortraitEngine.Options o=new PrismaticDiagonalPortraitEngine.Options();
        o.detail=c.detail;o.layers=c.colorCount;o.widthMm=c.widthMm;o.minCutMm=c.minimumMm;
        o.gapMm=c.prismaticGapMm;o.irregularity=c.prismaticIrregularity;o.roundness=c.prismaticRoundness;
        o.featureEmphasis=c.prismaticFeature;o.toneBalance=c.prismaticTone;o.palette=c.palette;o.paperArgb=c.paper;
        check(o.conditionGapMm==0f,"conditioning is opt-in at the engine level");
        String sig=signature(PrismaticDiagonalPortraitEngine.generate(portrait(72,90),72,90,o));
        check(sig.equals(DIAGONAL_559),"Prismatic Diagonal 559 engine output changed:\n"+sig+"\nvs 559:\n"+DIAGONAL_559);
        StyleKit.Result legacy=PrismaticDiagonalPortraitEngine.generate(portrait(72,90),72,90,o);
        StyleKit.Result production=VinylStyleSuite.generate(portrait(72,90),72,90,c);
        check(production.layers.size()==legacy.layers.size(),"conditioning keeps every Diagonal layer");
        check(production.summary.contains("cut conditioning"),"production Diagonal reports its conditioning");
        float gap=StyleCutGeometryPreflight.exportGapMm(.5f,c.minimumMm);
        int before=StyleCutGeometryPreflight.inspect(legacy,c.minimumMm,gap,100000).size();
        int after=StyleCutGeometryPreflight.inspect(production,c.minimumMm,gap,100000).size();
        for(int i=1;i<legacy.layers.size();i++){
            double a=0,b=0;
            for(float[] p:legacy.layers.get(i).loops)a+=StyleKit.signedArea(p);
            for(float[] p:production.layers.get(i).loops)b+=StyleKit.signedArea(p);
            check(Math.abs(Math.abs(b)-Math.abs(a))<=.12*Math.abs(a)+2,"layer "+i+" area moved more than 12%: "+a+" -> "+b);
        }
        System.out.println("  Diagonal findings 559 engine "+before+" -> production "+after);
        check(after<=Math.max(3,before/5),"conditioning must remove most Diagonal findings: "+before+" -> "+after);
    }
    static void engine()throws Exception{
        int w=72,h=90;int[] photo=portrait(w,h);
        HashSet<String> distinct=new HashSet<>();
        distinct.add(signature(VinylStyleSuite.generate(photo,w,h,config(VinylStyleSuite.PRISMATIC_DIAGONAL,101.6f,6,PALETTE))));
        JtsVectorBooleanBackend.CONDITION_FALLBACKS.set(0);
        for(int id:RIBBONS){
            String name=VinylStyleSuite.NAMES[id];
            VinylStyleSuite.Config c=config(id,101.6f,6,PALETTE);
            long t0=System.nanoTime();
            StyleKit.Result a=VinylStyleSuite.generate(photo,w,h,c);
            long ms=(System.nanoTime()-t0)/1_000_000L;
            StyleKit.Result b=VinylStyleSuite.generate(photo,w,h,c.copy());
            String sig=signature(a);
            check(sig.equals(signature(b)),"deterministic "+name);
            check(distinct.add(sig),"geometry differs from Diagonal and siblings: "+name);
            check(a.summary.startsWith(name+" · "),"summary names the style: "+a.summary);
            check(a.summary.contains("independent vector Boolean overlays"),"production vector route "+name);
            for(int i=0;i<a.layers.size();i++){
                StyleKit.Layer l=a.layers.get(i);int slot=NewStyleColorSlots.slot(id,l.name,i);
                check(l.color==PALETTE[slot],"manual swatch kept at its physical slot "+l.name);
                check(l.vectorGeometryLocked,"locked vector sheet "+l.name);
            }
            check(NewStyleColorSlots.slot(id,a.layers.get(0).name,0)==0,"L1 is the Snip foundation");
            StyleKit.Result cut=VinylStyleSuite.prepareForCutting(a,id,c.minimumMm,.5f);
            for(int i=0;i<a.layers.size();i++)check(a.layers.get(i).loops.equals(cut.layers.get(i).loops),"cut prep keeps locked sheets "+name);
            int findings=StyleCutGeometryPreflight.inspect(a,c.minimumMm,c.gapMm,100000).size();
            int contours=0;for(StyleKit.Layer l:a.layers)contours+=l.loops.size();
            System.out.printf(Locale.US,"  %-24s layers=%d contours=%d findings=%d %dms%n",name,a.layers.size(),contours,findings,ms);
            check(findings<=Math.max(3,contours/20),"post-Boolean cut findings bounded for "+name+": "+findings);
            exports(a,name);
        }
        check(JtsVectorBooleanBackend.CONDITION_FALLBACKS.get()==0,"cut conditioning never fell back");
        // 2/4/8 layers, reversed manual palette: colors keep their physical slots.
        int[] reversed=new int[8];for(int i=0;i<8;i++)reversed[i]=PALETTE[7-i];
        for(int layers:new int[]{2,4,8}){
            VinylStyleSuite.Config c=config(VinylStyleSuite.PRISMATIC_CHEVRON,101.6f,layers,reversed);
            StyleKit.Result r=VinylStyleSuite.generate(photo,w,h,c);
            check(!r.layers.isEmpty()&&r.layers.size()<=layers,"layer count "+layers);
            for(int i=0;i<r.layers.size();i++){
                int slot=NewStyleColorSlots.slot(c.style,r.layers.get(i).name,i);
                check(slot<layers&&r.layers.get(i).color==reversed[slot],"reversed swatch at slot "+slot);
            }
        }
        // Automatic palette (no manual swatches) still yields exactly the requested slots.
        StyleKit.Result auto=PrismaticRibbonPortraitEngine.generate(photo,w,h,options(PrismaticRibbonPortraitEngine.Mode.SILK,101.6f,5));
        check(auto.layers.size()==5,"stable physical slots including empty ones");
        // Palette length must match the physical layer count.
        boolean rejected=false;
        try{VinylStyleSuite.Config c=config(VinylStyleSuite.PRISMATIC_TWILL,101.6f,4,PALETTE);c.palette=Arrays.copyOf(PALETTE,3);
            VinylStyleSuite.generate(photo,w,h,c);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"palette/slot mismatch rejected");
        snipWithHole(photo,w,h);
        sizes();
        cancellation(photo,w,h);
    }
    static PrismaticRibbonPortraitEngine.Options options(PrismaticRibbonPortraitEngine.Mode mode,float widthMm,int layers){
        PrismaticRibbonPortraitEngine.Options o=new PrismaticRibbonPortraitEngine.Options();
        o.mode=mode;o.widthMm=widthMm;o.layers=layers;o.minCutMm=.4f;o.gapMm=.5f;return o;
    }
    static void exports(StyleKit.Result r,String name)throws Exception{
        for(int i=0;i<r.layers.size();i++){
            if(r.layers.get(i).loops.isEmpty())continue;
            String svg=NewStyleExport.layerSvg(r,i,true,0,3f,2f,null,.4f);
            CutSvgCompatibility.verify(svg,OutputDeviceProfile.GENERIC_SVG);
            SvgDxfR13Exporter.Result dxf=SvgDxfR13Exporter.convert(svg);
            SheetFrame frame=SheetFrame.of(r.widthMm,r.heightMm,true,3f,2f,null,0f,false);
            check(Math.abs(dxf.widthMm-frame.boardWidthMm())<.01&&Math.abs(dxf.heightMm-frame.boardHeightMm())<.01,
                "DXF keeps the physical sheet size "+name);
            check(dxf.closedContours>=r.layers.get(i).loops.size(),"DXF keeps every contour (holes included) "+name);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(ZipOutputStream zip=new ZipOutputStream(out)){
                CutSheetZipWriter cuts=new CutSheetZipWriter(zip,OutputDeviceProfile.GENERIC_SVG,.4f,true);
                cuts.put("layer.svg",svg);cuts.finish();
            }
            check(out.size()>100,"cut ZIP written "+name);
        }
    }
    /** Circular Snip with a hole: no overlay vinyl outside the Snip or inside the hole. */
    static void snipWithHole(int[] photo,int w,int h){
        boolean[] allowed=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double dx=x+.5-w/2.0,dy=y+.5-h/2.0,r=Math.hypot(dx,dy);
            allowed[y*w+x]=r<w*.46&&Math.hypot(dx-6,dy+8)>7;
        }
        for(int id:new int[]{VinylStyleSuite.PRISMATIC_CONTOUR_FLOW,VinylStyleSuite.PRISMATIC_BRAIDED_FLOW}){
            VinylStyleSuite.Config c=config(id,101.6f,5,PALETTE);c.allowed=allowed;
            StyleKit.Result r=VinylStyleSuite.generate(photo,w,h,c);
            float px=r.widthMm/w;int outside=0,probes=0;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                // Probe pixel centres a full pixel away from any allowed pixel.
                boolean far=true;
                for(int yy=Math.max(0,y-1);yy<=Math.min(h-1,y+1)&&far;yy++)
                    for(int xx=Math.max(0,x-1);xx<=Math.min(w-1,x+1);xx++)if(allowed[yy*w+xx]){far=false;break;}
                if(!far)continue;probes++;
                float mx=(x+.5f)*px,my=(y+.5f)*px;
                for(int li=1;li<r.layers.size();li++){
                    boolean in=false;for(float[] p:r.layers.get(li).loops)if(MaskedFacetClipper.inside(p,mx,my))in=!in;
                    if(in){outside++;break;}
                }
            }
            check(probes>200,"Snip probes");
            check(outside==0,"no vinyl outside the Snip or inside its hole: "+VinylStyleSuite.NAMES[id]+" "+outside);
        }
    }
    /** 4/6/8/11 in, portrait and landscape: completes, stays on the artboard. */
    static void sizes(){
        int[][] dims={{60,84},{84,60}};
        for(int[] d:dims){
            int[] p=portrait(d[0],d[1]);
            for(float inches:new float[]{4,6,8,11}){
                VinylStyleSuite.Config c=config(VinylStyleSuite.PRISMATIC_SILK,inches*25.4f,6,PALETTE);
                long t0=System.nanoTime();
                StyleKit.Result r=VinylStyleSuite.generate(p,d[0],d[1],c);
                long ms=(System.nanoTime()-t0)/1_000_000L;
                check(Math.abs(r.widthMm-inches*25.4f)<.01f,"physical width "+inches);
                for(StyleKit.Layer l:r.layers)for(float[] loop:l.loops)for(int k=0;k<loop.length;k+=2)
                    check(loop[k]>=-.001f&&loop[k]<=r.widthMm+.001f&&loop[k+1]>=-.001f&&loop[k+1]<=r.heightMm+.001f,"on artboard");
                SheetFrame frame=SheetFrame.of(r.widthMm,r.heightMm,true,3f,2f,null,0f,false);
                System.out.printf(Locale.US,"  Silk %dx%d %.0fin %dms board %.1fx%.1f mm%s%n",d[0],d[1],inches,ms,
                    frame.boardWidthMm(),frame.boardHeightMm(),
                    Math.max(frame.boardWidthMm(),frame.boardHeightMm())>292.1f?"  (larger than a 12x12 mat: refused unless the 12x24 mat is selected; see MatFitQualificationTest)":"");
            }
        }
    }
    static void cancellation(int[] photo,int w,int h)throws Exception{
        AtomicReference<Throwable> seen=new AtomicReference<>();
        Thread t=new Thread(()->{
            try{VinylStyleSuite.generate(photo,w,h,config(VinylStyleSuite.PRISMATIC_BRAIDED_FLOW,279.4f,8,PALETTE));}
            catch(Throwable e){seen.set(e);}
        });
        t.start();Thread.sleep(150);t.interrupt();t.join(20000);
        check(!t.isAlive(),"render stops after cancellation");
        Throwable e=seen.get();
        check(e==null||e instanceof CancellationException,"cancellation surfaces as CancellationException: "+e);
    }
}

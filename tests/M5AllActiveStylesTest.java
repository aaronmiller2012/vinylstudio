package com.halftonestudio;

import java.util.ArrayList;
import java.util.Locale;

/**
 * M5 regression matrix over EVERY active (non-retired, selectable) suite style id.
 * Small photo, small width. Asserts: renders; layers match the palette contract; the saved
 * style id/variant/selection round-trips through the real restore helpers; a Config copy
 * regenerates identical geometry (deterministic restore) and is independent of the original
 * (duplicate). Also prints an informational "slider effect" table (Detail) used by the audit.
 */
public final class M5AllActiveStylesTest {
    static int[] portrait(int w,int h){
        int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double fx=(x-w*.5)/(w*.5),fy=(y-h*.49)/(h*.5);
            double detail=.42+.26*Math.cos(fx*11)*Math.sin(fy*13);
            double face=Math.max(0,1.0-(fx*fx*1.2+fy*fy));
            int k=(int)Math.max(5,Math.min(250,255*(.25+.58*face+.16*detail)));
            p[y*w+x]=0xff000000|(k<<16)|(((k*5)/6)<<8)|((k*3)/4);
        }
        return p;
    }
    static final int[] SWATCHES={0xff1f242b,0xffd65b90,0xff20a5bb,0xffe0b746,0xff435c83,0xffb65c46,0xff6b9471,0xffae8bba,0xffeeeeee};

    static VinylStyleSuite.Config config(int style,int variant,int w,int h,int detail){
        VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();
        cfg.style=style;cfg.variant=variant;cfg.widthMm=101.6f;cfg.detail=detail;
        // Same clamp the editor applies (startNewStyleRender): at least 2 layers (3 for Glass).
        cfg.colorCount=Math.max(style==VinylStyleSuite.GLASS?3:2,Math.min(NewStyleColorSlots.maximumSelectable(style),4));
        cfg.minimumMm=.254f;cfg.weightMm=.7f;cfg.draft=false;
        cfg.gapMm=VinylStyleSuite.generationGapMm(.5f,.254f);
        if(VinylStyleSuite.isPopPoster(style)){cfg.popPoster=new PopPosterEngine.Options();cfg.popPoster.toneCount=cfg.colorCount;}
        if(NewStyleColorSlots.multi(style))
            cfg.palette=NewStyleColorSlots.fixedPalette(style,variant,cfg.colorCount,SWATCHES);
        cfg.allowed=new boolean[w*h];java.util.Arrays.fill(cfg.allowed,true);
        return cfg;
    }
    static String signature(StyleKit.Result r){
        long h=1125899906842597L;StringBuilder s=new StringBuilder();
        for(StyleKit.Layer l:r.layers){
            h=31*h+l.color;h=31*h+l.loops.size();h=31*h+l.lines.size();h=31*h+l.pointCount();
            for(float[] loop:l.loops)for(float v:loop)h=31*h+Float.floatToIntBits(v);
        }
        return r.layers.size()+":"+h;
    }
    static void require(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}

    public static void main(String[] args)throws Exception{
        final int W=48,H=48;int[] px=portrait(W,H);
        ArrayList<String> failures=new ArrayList<>();int tested=0,variants=0;
        StringBuilder sliderReport=new StringBuilder();
        for(int style=0;style<VinylStyleSuite.NAMES.length;style++){
            if(VinylStyleSuite.isRetiredStyleId(style)||!NewStyleVariantPolicy.selectable(style))continue;
            tested++;
            String name=VinylStyleSuite.NAMES[style];
            try{
                // Saved-id / selection round trip through the real restore helpers.
                require(VinylStyleSuite.restoredStyleId(style)==style,"restoredStyleId changed id");
                StyleSelection sel=StyleSelection.suite(style);
                require(sel.suiteId==style,"StyleSelection.suite lost id");
                int n=NewStyleVariantPolicy.count(style);
                for(int v=0;v<Math.min(n,6);v++){
                    require(NewStyleVariantPolicy.restore(style,v)==v||style==VinylStyleSuite.TESSELLATION_MOTIF||style==VinylStyleSuite.BANKNOTE,
                        "variant "+v+" did not restore");
                    variants++;
                    VinylStyleSuite.Config cfg=config(style,v,W,H,30);
                    StyleKit.Result r=VinylStyleSuite.generate(px,W,H,cfg);
                    require(r!=null&&!r.layers.isEmpty(),"variant "+v+" rendered no layers");
                    boolean any=false;for(StyleKit.Layer l:r.layers)any|=!l.loops.isEmpty()||!l.lines.isEmpty();
                    require(any,"variant "+v+" produced only empty layers");
                    // Layer count must not exceed the physical slots the palette defines.
                    int slots=NewStyleColorSlots.slotCount(style,v,cfg.colorCount);
                    require(r.layers.size()<=Math.max(slots,cfg.palette==null?slots:cfg.palette.length)+1,
                        "variant "+v+" has "+r.layers.size()+" layers for "+slots+" slots");
                    for(int i=0;i<r.layers.size();i++){
                        int slot=NewStyleColorSlots.slot(style,r.layers.get(i).name,i);
                        require(slot>=0&&slot<9,"layer '"+r.layers.get(i).name+"' maps to invalid slot "+slot);
                    }
                    if(v==0){
                        // Restore: a copy of the saved config regenerates identical geometry.
                        VinylStyleSuite.Config saved=cfg.copy();
                        String first=signature(r);
                        String restored=signature(VinylStyleSuite.generate(px,W,H,saved));
                        require(first.equals(restored),"restored config produced different geometry");
                        // Duplicate: mutating the duplicate never touches the original.
                        VinylStyleSuite.Config dup=cfg.copy();
                        if(dup.palette!=null)dup.palette[0]^=0x00ffffff;
                        dup.allowed[0]=false;dup.words="DUP";dup.detail=99;
                        require(cfg.allowed[0]&&cfg.detail==30&&!"DUP".equals(cfg.words),"duplicate shares state");
                        if(cfg.palette!=null)require(cfg.palette[0]==SWATCHES[0]||cfg.palette[0]!=dup.palette[0],"duplicate shares palette array");
                        require(first.equals(signature(VinylStyleSuite.generate(px,W,H,cfg))),"original changed after duplicating");
                        // Informational: does Detail change geometry?
                        String lo=signature(VinylStyleSuite.generate(px,W,H,config(style,0,W,H,15)));
                        String hi=signature(VinylStyleSuite.generate(px,W,H,config(style,0,W,H,90)));
                        String verdict="changes geometry";
                        if(lo.equals(hi)){
                            int[] big=portrait(144,144);
                            String lo2=signature(VinylStyleSuite.generate(big,144,144,config(style,0,144,144,15)));
                            String hi2=signature(VinylStyleSuite.generate(big,144,144,config(style,0,144,144,90)));
                            verdict=lo2.equals(hi2)?"NO EFFECT at 48px and 144px":"no effect at 48px, changes at 144px";
                        }
                        sliderReport.append(String.format(Locale.US,"  detail %-34s %s%n",name,verdict));
                    }
                }
                System.out.println("PASS "+style+" "+name);
            }catch(Throwable t){
                failures.add(style+" "+name+": "+t.getClass().getSimpleName()+" "+t.getMessage());
                System.out.println("FAIL "+failures.get(failures.size()-1));
            }
        }
        System.out.print(sliderReport);
        System.out.printf(Locale.US,"M5 active styles: tested=%d variants=%d failures=%d%n",tested,variants,failures.size());
        require(tested>=30,"too few active styles discovered: "+tested);
        if(!failures.isEmpty())throw new AssertionError(String.join(" | ",failures));
        System.out.println("PASS M5AllActiveStylesTest");
    }
}

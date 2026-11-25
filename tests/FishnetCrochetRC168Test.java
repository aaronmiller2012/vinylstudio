package com.halftonestudio;

import java.util.Arrays;

/** Real geometry, fabric structure, layer and interrupt coverage for RC168. */
public final class FishnetCrochetRC168Test {
    static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
    static final int W=104,H=123;
    static boolean[] silhouette(){
        boolean[] m=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            double dx=(x-W*.50)/(W*.44),dy=(y-H*.51)/(H*.46);
            m[y*W+x]=dx*dx+dy*dy<1;
        }
        return m;
    }
    static int[] photo(boolean flat){
        int[] p=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float t=flat?.52f:.21f+.62f*(.61f*x/W+.39f*y/H);
            if(!flat){
                if(y>H*.30&&y<H*.38&&((x>W*.28&&x<W*.41)||(x>W*.57&&x<W*.70)))t-=.38f;
                if(y>H*.68&&y<H*.73&&x>W*.34&&x<W*.66)t-=.35f;
            }
            int v=(int)(255f*StyleKit.clampF(t,0,1));
            p[y*W+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        return p;
    }
    static int filled(boolean[] b){int n=0;for(boolean x:b)if(x)n++;return n;}
    static int filled(PatternPortraitTrioEngine.Surface s){return filled(s.ink[0]);}
    static void lattice(){
        int[] a=photo(false);boolean[] mask=silhouette();
        StyleKit.Source src=new StyleKit.Source(a,W,H,mask,279.4f);
        NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();
        o.pattern=NineLayerCraftPatternEngine.CROCHET;o.widthMm=279.4f;o.detail=80;o.draft=false;
        float pitch=NineLayerCraftPatternEngine.textilePitch(src,o);
        java.util.ArrayList<NineLayerCraftPatternEngine.Cell> c=NineLayerCraftPatternEngine.createCells(src,o,pitch);
        for(NineLayerCraftPatternEngine.Cell v:c){
            float xExpected=(v.col+.5f)*pitch;
            require(Math.abs(v.x-xExpected)<.0005f,"Crochet columns must not stagger");
        }
        o.pattern=NineLayerCraftPatternEngine.FISHNET;
        float fp=NineLayerCraftPatternEngine.textilePitch(src,o);
        c=NineLayerCraftPatternEngine.createCells(src,o,fp);
        for(NineLayerCraftPatternEngine.Cell v:c){
            float expected=NineLayerCraftPatternEngine.fishnetNodeX(v.row,v.col,fp);
            float expectedY=NineLayerCraftPatternEngine.fishnetNodeY(v.row,v.col,fp);
            require(Math.abs(v.x-expected)<.0005f&&Math.abs(v.y-expectedY)<.0005f,
                "Fishing net shared knot lattice mismatch");
        }
        require(pitch<5f,"Default detailed textile stitch is too coarse at 11 inches");
        for(int id:new int[]{VinylStyleSuite.FISHNET,VinylStyleSuite.CROCHET}){
            int selected=PhotoStyleDefaults.forNewStyle(id,null,.254f,279.4f).detail;
            require(selected>=82,"Textile defaults must favor detail at eleven inches");
            require(NewStyleColorSlots.maximumSelectable(id)==9,"Nine-sheet support lost");
            require(NewStyleLayerPolicy.isStructuralLayer(id,0),"Solid silhouette base not protected");
        }
        System.out.println("PASS RC168 stable aligned crochet mesh + staggered knot lattice, pitch="+pitch);
    }
    static void primitive(){
        final int sz=100;int[] px=new int[sz*sz];Arrays.fill(px,0xff777777);
        StyleKit.Source src=new StyleKit.Source(px,sz,sz,null,100f);
        NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();
        o.widthMm=100f;o.minCutMm=.254f;
        PatternPortraitTrioEngine.Surface open=new PatternPortraitTrioEngine.Surface(src,1,o.minCutMm,true);
        PatternPortraitTrioEngine.Surface closed=new PatternPortraitTrioEngine.Surface(src,1,o.minCutMm,true);
        NineLayerCraftPatternEngine.Cell light=new NineLayerCraftPatternEngine.Cell(49f,49f,.84f,.05f,123L,0,0);
        NineLayerCraftPatternEngine.Cell dark=new NineLayerCraftPatternEngine.Cell(49f,49f,.22f,.05f,123L,0,0);
        float stroke=NineLayerCraftPatternEngine.safeStroke(open,o);
        NineLayerCraftPatternEngine.crochet(open,light,8f,stroke,o);
        NineLayerCraftPatternEngine.crochet(closed,dark,8f,stroke,o);
        int a=filled(open),b=filled(closed);
        require(b>a*1.16,"Filled filet block must have distinctly more yarn than an open mesh: "+a+" vs "+b);
        PatternPortraitTrioEngine.Surface fish=new PatternPortraitTrioEngine.Surface(src,1,o.minCutMm,true);
        int row=7,col=6;
        float nx=NineLayerCraftPatternEngine.fishnetNodeX(row,col,8f);
        float ny=NineLayerCraftPatternEngine.fishnetNodeY(row,col,8f);
        NineLayerCraftPatternEngine.Cell knot=new NineLayerCraftPatternEngine.Cell(nx,ny,.22f,.05f,123L,row,col);
        NineLayerCraftPatternEngine.fishnet(fish,knot,8f,stroke,o);
        int centerX=(int)(nx/fish.mm),centerY=(int)(ny/fish.mm);
        int below=0,above=0;
        for(int y=0;y<fish.h;y++)for(int x=0;x<fish.w;x++){
            if(!fish.ink[0][y*fish.w+x])continue;
            if(y>centerY+4)below++;
            if(y<centerY-4)above++;
        }
        require(below>0 && above==0,"Fishnet should emit two downward edges only; no duplicate up-strands");
        System.out.println("PASS RC168 filled vs open crochet cells: "+a+" -> "+b+" px; unique outgoing fishnet cords");
    }
    static void full(){
        boolean[] mask=silhouette();
        int[] manual={0xff322923,0xff574331,0xff745845,0xff8e6b56,0xffaa8468,0xffbb987f,
            0xffd0ae93,0xffe0c4a9,0xfff5dfc5};
        for(int type:new int[]{NineLayerCraftPatternEngine.FISHNET,NineLayerCraftPatternEngine.CROCHET}){
            for(int layerCount:new int[]{2,4,9}){
                for(boolean flat:new boolean[]{false,true}){
                    NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();
                    o.pattern=type;o.widthMm=279.4f;o.detail=80;o.layers=layerCount;
                    o.minCutMm=.254f;o.allowed=mask;o.draft=true;
                    o.palette=Arrays.copyOf(manual,layerCount);
                    StyleKit.Result r=NineLayerCraftPatternEngine.generate(photo(flat),W,H,o);
                    require(r.layers.size()==layerCount,"Physical sheet count wrong");
                    for(int i=0;i<layerCount;i++){
                        StyleKit.Layer l=r.layers.get(i);
                        require(!l.loops.isEmpty(),"Empty L"+(i+1)+" for "+type);
                        require(l.color==manual[i],"Color swatch order changed");
                        require(l.lines.isEmpty(),"Not cutter-ready fill geometry");
                        for(float[] contour:l.loops){
                            require(contour.length>=6,"Invalid cut outline");
                            for(float v:contour)require(Float.isFinite(v),"Invalid cut coordinate");
                        }
                    }
                    System.out.println("PASS RC168 "+NineLayerCraftPatternEngine.NAMES[type]+
                        " "+layerCount+" layer(s), "+(flat?"flat":"portrait")+" tone, filled sheets");
                }
            }
        }
        boolean interrupted=Thread.interrupted();
        require(!interrupted,"Test thread unexpectedly interrupted");
        Thread.currentThread().interrupt();
        try{
            NineLayerCraftPatternEngine.Options o=new NineLayerCraftPatternEngine.Options();
            o.pattern=NineLayerCraftPatternEngine.FISHNET;o.widthMm=279.4f;o.allowed=mask;
            NineLayerCraftPatternEngine.generate(photo(false),W,H,o);
            throw new AssertionError("Cancelled render ran to completion");
        }catch(java.util.concurrent.CancellationException expected){
            Thread.interrupted();
        }catch(RuntimeException expected){
            require(Thread.interrupted(),"Cancellation should not be swallowed: "+expected);
        }
        System.out.println("PASS RC168 interrupt honored during expensive textile generation");
    }
    public static void main(String[] args){lattice();primitive();full();}
}

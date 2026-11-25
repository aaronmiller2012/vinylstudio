package com.halftonestudio;

import java.util.HashSet;

/** Shape mix, continuous random scatter, pile overlap, swatch identity, and Snip base. */
public final class FallenConfettiRC155Test {
    static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    static final int W=180,H=212;
    static int[] photo(){
        int[] pix=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int r=52+(160*x/W),g=35+(150*y/H),b=41+(85*x/W);
            if(x>56&&x<82&&y>54&&y<92){r=g=b=18;}
            if(x>104&&x<130&&y>56&&y<94){r=g=b=18;}
            pix[y*W+x]=0xFF000000|(r<<16)|(g<<8)|b;
        }
        return pix;
    }
    static boolean[] mask(){
        boolean[] valid=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float a=(x-W*.5f)/(W*.47f),b=(y-H*.51f)/(H*.47f);
            valid[y*W+x]=a*a+b*b<1f && !(x>83&&x<95&&y>116&&y<126);
        }
        return valid;
    }
    public static void main(String[] args){
        int[] original=photo();boolean[] mask=mask();
        PatternPortraitTrioEngine.Options opt=new PatternPortraitTrioEngine.Options();
        opt.kind=PatternPortraitTrioEngine.CONFETTI;
        opt.layers=6;opt.widthMm=279.4f;opt.detail=78;
        opt.spacingMm=.35f;opt.organic=70;opt.minCutMm=.254f;
        opt.allowed=mask;
        opt.palette=new int[]{0xFF41291F,0xFFEDBBA3,0xFF6B4C70,0xFFAA6642,0xFF252A35,0xFFD89C68};
        int[] manual=opt.palette.clone();
        StyleKit.Source source=new StyleKit.Source(original,W,H,mask,opt.widthMm).autoLevels(.02f,.98f);
        FallenConfettiEngine.Plan scatter=FallenConfettiEngine.sample(source,opt,manual);
        check(scatter.primary>400,"too few scattered primary pieces");
        check(scatter.clustered>80,"missing smaller piled fragments");
        check(scatter.overlaps>30,"no naturally overlapping clusters");
        for(int type=0;type<3;type++)check(scatter.types[type]>45,"missing distinct shape family "+type);
        int[] hist=new int[12];
        HashSet<Integer> uniqueX=new HashSet<>();
        for(FallenConfettiEngine.Piece p:scatter.pieces){
            float fraction=(p.x/scatter.pitch)%1f;
            hist[Math.min(11,(int)(fraction*12))]++;
            uniqueX.add(Math.round(p.x*20f));
            check(p.slot>0 && p.slot<manual.length,"base slot used by particle");
            float[] outline=FallenConfettiEngine.polygon(p);
            check(outline.length>=8,"invalid polygon");
            check(StyleKit.selfIntersections(outline,1)==0,"self intersecting flake");
        }
        int max=0;
        for(int n:hist)max=Math.max(max,n);
        check(max<scatter.pieces.size()*.18f,"regular placement grid still detectable");
        check(uniqueX.size()>scatter.pieces.size()*.65f,"many repeated grid x coordinates");
        PatternPortraitTrioEngine.Surface surface=new PatternPortraitTrioEngine.Surface(source,opt.layers,opt.minCutMm);
        int pieces=FallenConfettiEngine.render(surface,opt,manual);
        check(pieces==scatter.pieces.size(),"non-deterministic scatter");
        int base=0,exposed=0,overlap=0;
        for(int i=0;i<surface.valid.length;i++)if(surface.valid[i]){
            base++;
            check(surface.ink[0][i],"Snip has a hole in the solid base");
            int upper=0;
            for(int j=1;j<opt.layers;j++)if(surface.ink[j][i])upper++;
            if(upper==0)exposed++;
            if(upper>=2)overlap++;
        }
        check(exposed>base*.17f,"base covered by too many pieces: "+exposed+"/"+base);
        check(overlap>base*.01f,"different-color pieces not actually overlapping");
        StyleKit.Result output=PatternPortraitTrioEngine.generate(original,W,H,opt);
        check(output.layers.size()==manual.length,"physical sheets changed");
        for(int k=0;k<manual.length;k++)check(output.layers.get(k).color==manual[k],"manual swatch order moved: "+k);
        check(output.summary.contains("scattered"),"old regular-grid engine still selected");
        for(StyleKit.Layer sheet:output.layers){
            check(sheet.lines.isEmpty(),"stroke-only vector export");
            for(float[] loop:sheet.loops){
                check(loop.length>=6,"bad cut loop");
                check(StyleKit.selfIntersections(loop,1)==0,"self-intersecting cut loop");
            }
        }
        PatternPortraitTrioEngine.Options automatic=new PatternPortraitTrioEngine.Options();
        automatic.kind=PatternPortraitTrioEngine.CONFETTI;
        automatic.layers=5;automatic.widthMm=152.4f;automatic.detail=50;
        automatic.allowed=mask;
        StyleKit.Result auto=PatternPortraitTrioEngine.generate(original,W,H,automatic);
        for(int k=1;k<auto.layers.size();k++)
            check(StyleKit.luma255(auto.layers.get(k-1).color)<=StyleKit.luma255(auto.layers.get(k).color),"auto palette lacks tonal progression");
        // Two-layer fallback must still retain solid L1 and an upper confetti sheet.
        opt.layers=2;opt.palette=new int[]{0xFF271C1A,0xFFECC1A5};
        StyleKit.Result pair=PatternPortraitTrioEngine.generate(original,W,H,opt);
        check(pair.layers.size()==2 && !pair.layers.get(0).loops.isEmpty() && !pair.layers.get(1).loops.isEmpty(),"two-layer failure");
        System.out.println("PASS RC155: natural scatter, three shapes, clustered overlap, solid base, swatch preservation, 2-6 layers");
        System.out.println("pieces="+scatter.pieces.size()+" primary="+scatter.primary+" clusters="+scatter.clustered+
            " overlapCandidates="+scatter.overlaps+" exposedBase="+(100f*exposed/base)+"% multiOverlay="+(100f*overlap/base)+"%");
    }
}

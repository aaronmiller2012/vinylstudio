package com.halftonestudio;

import java.io.File;
import java.awt.image.BufferedImage;
import java.util.*;

/** Compare coarse and adaptive scattering with identical photos, palettes and shape seed.
 * Measures the actual traced opaque raster-fallback composite, not a model prediction.
 * Full vector qualification is a separate NAS/device obligation. */
public final class M6ConfettiDetailTest {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static class Trial {
        int count; double tone,ssim,edge,de,base; int loops;
    }
    static Trial evaluate(M4ArtBench.Photo p,PatternPortraitTrioEngine.Options o,float adaptive)throws Exception{
        StyleKit.Source src=new StyleKit.Source(p.argb,p.w,p.h,o.allowed,o.widthMm).autoLevels(.02f,.98f);
        FallenConfettiEngine.Plan plan=adaptive==0?FallenConfettiEngine.sample(src,o,o.palette,0):FallenConfettiEngine.sample(src,o,o.palette);
        PatternPortraitTrioEngine.Surface surface=new PatternPortraitTrioEngine.Surface(src,o.layers,o.minCutMm,o.gapMm,false,false);
        FallenConfettiEngine.renderPlan(surface,o,plan);
        long valid=0,base=0;
        for(int i=0;i<surface.valid.length;i++)if(surface.valid[i]){
            valid++;check(surface.ink[0][i],"base not filled");boolean visible=true;
            for(int k=1;k<o.layers;k++)if(surface.ink[k][i]){visible=false;break;}
            if(visible)base++;
        }
        StyleKit.Result result=surface.finish(o.palette,"confetti",plan.pieces.size(),o.minCutMm);
        M4ArtBench.Render rr=new M4ArtBench.Render();rr.widthMm=result.widthMm;rr.heightMm=result.heightMm;rr.paper=0xffff00ff;
        Trial out=new Trial();out.count=plan.pieces.size();out.base=base/(double)valid;
        for(int i=0;i<result.layers.size();i++){
            StyleKit.Layer l=result.layers.get(i);check(l.color==o.palette[i],"palette order changed");
            M4ArtBench.Sheet sh=new M4ArtBench.Sheet();sh.color=l.color;sh.loops.addAll(l.loops);rr.sheets.add(sh);out.loops+=l.loops.size();
        }
        BufferedImage comp=M4ArtBench.composite(rr,Math.min(3f,480f/Math.max(rr.widthMm,rr.heightMm)));
        String[] m=M4ArtBench.metrics(rr,p,comp).split("\t");
        out.tone=Double.parseDouble(m[0]);out.ssim=Double.parseDouble(m[1]);out.edge=Double.parseDouble(m[2]);out.de=Double.parseDouble(m[3]);
        return out;
    }
    public static void main(String[] args)throws Exception{
        boolean full=args.length>0&&args[0].equals("full");
        String[] names=full?new String[]{"astronaut.png","camera.png","syn-glasses","syn-dark-skin"}:new String[]{"astronaut.png","syn-glasses"};
        float[] sizes=full?new float[]{101.6f,152.4f,203.2f,279.4f}:new float[]{152.4f};
        double oldTone=0,newTone=0,oldSsim=0,newSsim=0,oldEdge=0,newEdge=0,oldDE=0,newDE=0;int n=0;
        System.out.println("fixture\tmode\tmm\toldPieces\tnewPieces\toldSSIM\tnewSSIM\toldEdge\tnewEdge\toldDE\tnewDE\toldBase\tnewBase\toldLoops\tnewLoops");
        for(String name:names){
            if(args.length>1&&!args[1].equals(name))continue;
            M4ArtBench.Photo p;
            if(name.startsWith("syn-")){int[] d=new int[2];int[] px=M5Fixtures.render(name,d);p=new M4ArtBench.Photo(name,px,d[0],d[1]);}
            else p=M4ArtBench.load(new File("tests/m4/photos",name),450);
            int[] ph=M4ArtBench.thumb(p,96);boolean[] v=new boolean[ph.length];Arrays.fill(v,true);
            int[] eight=PhotoPaletteOptimizer.optimize(ph,v,8).colors;
            int[] palette=Arrays.copyOf(VinylPaletteMapping.forStyle(VinylStyleSuite.CONFETTI,6,0xffffffff,eight,false,true,ph),6);
            for(float width:sizes)for(int mode=0;mode<=5;mode++){
                if(args.length>2&&Float.parseFloat(args[2])!=width)continue;
                if(args.length>3&&Integer.parseInt(args[3])!=mode)continue;
                PatternPortraitTrioEngine.Options o=new PatternPortraitTrioEngine.Options();o.widthMm=width;o.layers=6;o.palette=palette;o.minCutMm=.8f;o.gapMm=.5f;o.detail=82;o.organic=60;o.confettiMode=mode;
                Trial a=evaluate(p,o,0),b=evaluate(p,o,(o.detail-25)/75f);
                System.out.printf(Locale.US,"%s\t%d\t%.1f\t%d\t%d\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.3f\t%.3f\t%d\t%d%n",name,mode,width,a.count,b.count,a.ssim,b.ssim,a.edge,b.edge,a.de,b.de,a.base,b.base,a.loops,b.loops);
                check(b.count>a.count,"no increase in fragment count");check(b.base>.08,"foundation completely obscured");
                oldTone+=a.tone;newTone+=b.tone;oldSsim+=a.ssim;newSsim+=b.ssim;oldEdge+=a.edge;newEdge+=b.edge;oldDE+=a.de;newDE+=b.de;n++;
            }
        }
        System.out.printf(Locale.US,"mean %d: tone %.4f -> %.4f; SSIM %.4f -> %.4f; edge %.4f -> %.4f; dE %.4f -> %.4f%n",n,oldTone/n,newTone/n,oldSsim/n,newSsim/n,oldEdge/n,newEdge/n,oldDE/n,newDE/n);
        check(n>0,"no benchmark cases");
        check(newSsim>oldSsim,"mean SSIM did not improve");check(newEdge>oldEdge,"mean edge correlation did not improve");check(newDE<oldDE,"mean dE did not improve");
    }
}

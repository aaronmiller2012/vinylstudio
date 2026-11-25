package com.halftonestudio;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Six individual picker modes, physical swatch persistence and 4-in-1 weaving geometry. */
public final class ConfettiWeaveRC158Test {
    private static void check(boolean okay,String why){if(!okay)throw new AssertionError(why);}
    static final int W=112,H=138;
    static int[] portrait(boolean flat){
        int[] a=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float nx=x/(float)(W-1),ny=y/(float)(H-1);
            int r=flat?124:(int)(45+170*nx),g=flat?124:(int)(32+148*ny),b=flat?124:(int)(40+122*(1-nx));
            if(!flat&&x>28&&x<42&&y>40&&y<56)r=g=b=20;
            if(!flat&&x>65&&x<83&&y>40&&y<56)r=g=b=20;
            a[y*W+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return a;
    }
    static int[] swatches(int n){
        int[] palette={0xff46372a,0xfff2d6ae,0xff6f3f73,0xff128bad,0xffcd7b4e,
                       0xffd9b8da,0xff2c7865,0xff333348};
        return Arrays.copyOf(palette,n);
    }
    static void assertCuttable(StyleKit.Result result,int expected,int[] colors){
        check(result.layers.size()==expected,"physical layer count changed");
        for(int i=0;i<expected;i++){
            StyleKit.Layer l=result.layers.get(i);
            check(l.color==colors[i],"physical swatch index changed at L"+(i+1));
            check(!l.loops.isEmpty(),"empty physical layer L"+(i+1));
            check(l.lines.isEmpty(),"open pen-only path in vinyl layer");
            for(float[] p:l.loops){
                check(p.length>=6 && (p.length&1)==0,"malformed cut loop");
                for(float v:p)check(Float.isFinite(v),"non-finite cut coordinate");
            }
        }
    }
    static void confettiModes(){
        int[] ids={VinylStyleSuite.CONFETTI,VinylStyleSuite.CONFETTI_ROUND,
            VinylStyleSuite.CONFETTI_SQUARE,VinylStyleSuite.CONFETTI_PAPER,
            VinylStyleSuite.CONFETTI_STREAMER,VinylStyleSuite.CONFETTI_FALLEN};
        Set<String> summaries=new HashSet<>();
        StyleKit.Source src=new StyleKit.Source(portrait(false),W,H,null,152.4f).autoLevels(.02f,.98f);
        for(int id:ids){
            check(VinylStyleSuite.isConfetti(id),"unrouted confetti picker ID "+id);
            check(NewStyleVariantPolicy.selectable(id),"unselectable confetti ID "+id);
            check(NewStyleLayerPolicy.isStructuralLayer(id,0),"deletable base for "+id);
            check(NewStyleColorSlots.multi(id),"not multilayer for "+id);
            check(PaletteStylePolicy.familyForNewStyle(id)==PaletteStylePolicy.CONFETTI_STACK,"wrong swatch policy "+id);
            PatternPortraitTrioEngine.Options opt=new PatternPortraitTrioEngine.Options();
            opt.kind=PatternPortraitTrioEngine.CONFETTI;
            opt.confettiMode=VinylStyleSuite.confettiShapeMode(id);
            opt.detail=75;opt.organic=75;opt.layers=4;opt.widthMm=152.4f;
            opt.palette=swatches(4);opt.spacingMm=.35f;
            FallenConfettiEngine.Plan pieces=FallenConfettiEngine.sample(src,opt,opt.palette);
            check(pieces.primary>70&&pieces.clustered>10,"missing scatter in "+id);
            switch(opt.confettiMode){
                case FallenConfettiEngine.MODE_ROUND:
                    check(pieces.types[FallenConfettiEngine.DISC]==pieces.pieces.size(),"round includes other shapes");break;
                case FallenConfettiEngine.MODE_SQUARE:
                    check(pieces.types[FallenConfettiEngine.SQUARE]==pieces.pieces.size(),"square includes other shapes");break;
                case FallenConfettiEngine.MODE_PAPER:
                    check(pieces.types[FallenConfettiEngine.PAPER]==pieces.pieces.size(),"paper includes other shapes");break;
                case FallenConfettiEngine.MODE_STREAMER:
                    check(pieces.types[FallenConfettiEngine.STRIP]==pieces.pieces.size(),"streamer includes other shapes");break;
                default:
                    for(int n:pieces.types)check(n>5,"mixed/fallen omitted a shape family");
            }
            VinylStyleSuite.Config cfg=new VinylStyleSuite.Config();
            cfg.style=id;cfg.colorCount=4;cfg.palette=opt.palette;
            cfg.widthMm=152.4f;cfg.detail=75;cfg.minimumMm=.254f;
            cfg.trioOrganic=75;cfg.trioSpacingMm=.35f;
            StyleKit.Result result=VinylStyleSuite.generate(portrait(false),W,H,cfg);
            assertCuttable(result,4,cfg.palette);
            check(result.summary.contains("mode "+opt.confettiMode),"mode not passed into engine");
            summaries.add(result.summary);
        }
        check(summaries.size()==6,"confetti picker modes alias each other");
        check(VinylStyleSuite.restoredStyleId(VinylStyleSuite.CONFETTI)==VinylStyleSuite.CONFETTI,
            "original mixed confetti migration failed");
        check(VinylStyleSuite.isRetiredStyleId(VinylStyleSuite.SPIDER_WEB),"retired spider web revived");
    }
    static void chainmailTopologyAndSwatches(){
        int[] two=swatches(2),eight=swatches(8);
        for(int count:new int[]{2,8}){
            PatternPortraitTrioEngine.Options opt=new PatternPortraitTrioEngine.Options();
            opt.kind=PatternPortraitTrioEngine.CHAINMAIL;
            opt.layers=count;opt.palette=count==2?two:eight;
            opt.widthMm=152.4f;opt.detail=68;opt.organic=70;
            opt.spacingMm=.45f;
            for(boolean flat:new boolean[]{false,true}){
                int[] image=portrait(flat);
                StyleKit.Source src=new StyleKit.Source(image,W,H,null,opt.widthMm).autoLevels(.02f,.98f);
                InterlockingChainmailEngine.Plan plan=InterlockingChainmailEngine.plan(src,opt,opt.palette);
                check(plan.rings.size()>count*10,"too few rings");
                for(int i=1;i<opt.layers;i++)
                    check(plan.occupancy[i]>3,"ring color L"+(i+1)+" empty in quantile plan");
                // An interior ring has two candidates above and below, unlike
                // a donut grid where rings only abut their neighbors.
                boolean foundFour=false;
                for(InterlockingChainmailEngine.Ring a:plan.rings){
                    if(a.row<3||a.x<plan.pitchX*2||a.x>src.widthMm-plan.pitchX*2)continue;
                    int above=0,below=0;
                    for(InterlockingChainmailEngine.Ring b:plan.rings){
                        if(Math.abs(b.row-a.row)!=1)continue;
                        float dx=a.x-b.x;
                        if(Math.abs(dx)>plan.pitchX*.56f)continue;
                        if(b.row==a.row-1)above++;else below++;
                    }
                    if(above==2&&below==2){foundFour=true;break;}
                }
                check(foundFour,"four-neighbor chainmail topology absent");
                StyleKit.Result result=PatternPortraitTrioEngine.generate(image,W,H,opt);
                assertCuttable(result,count,opt.palette);
                check(result.summary.contains("four-in-one"),"old donut generator still active");
            }
        }
        check(NewStyleLayerPolicy.isStructuralLayer(VinylStyleSuite.CHAINMAIL,0),
            "chainmail base deletable");
    }
    public static void main(String[] args){
        confettiModes();
        chainmailTopologyAndSwatches();
        System.out.println("PASS RC158 six standalone Confetti modes and 4-in-1 ring topology, 2–8 filled physical layers");
    }
}

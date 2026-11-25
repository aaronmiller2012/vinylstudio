package com.halftonestudio;

import java.util.Arrays;

/** Compare the generated 0 and 100 percent physical color sheets over fine face features. */
public final class V3OrganicPortraitRetentionTest {
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    private static int[] portrait(int w,int h,boolean[] allowed){
        int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float u=(x-w*.5f)/(w*.44f),v=(y-h*.5f)/(h*.48f);
            boolean face=u*u+v*v<1;
            allowed[y*w+x]=face;
            int val=face?(int)(42+175*(.54f+.22f*u-.21f*v)):245;
            if(face && ((Math.abs(y-h*.37f)<h*.026f &&
                (Math.abs(x-w*.37f)<w*.058f||Math.abs(x-w*.63f)<w*.058f)) ||
                (Math.abs(y-h*.69f)<h*.023f&&Math.abs(x-w*.5f)<w*.15f) ||
                (Math.abs(x-w*.50f)<w*.027f&&Math.abs(y-h*.52f)<h*.055f)))val=15;
            val=StyleKit.clampI(val,0,255);
            p[y*w+x]=0xff000000|(val<<16)|((val+Math.min(24,255-val))<<8)|val;
        }
        return p;
    }
    public static void main(String[] args){
        int w=120,h=150;boolean[] allowed=new boolean[w*h];int[] photo=portrait(w,h,allowed);
        StyleKit.Source src=new StyleKit.Source(photo,w,h,allowed,101.6f);
        PatternPortraitTrioEngine.Surface surf=new PatternPortraitTrioEngine.Surface(src,6,.8f);
        for(int mode=0;mode<3;mode++){
            OrganicFlowPortraitEngine.Options o=new OrganicFlowPortraitEngine.Options();
            o.mode=mode;o.widthMm=101.6f;o.minCutMm=.8f;o.gapMm=.5f;
            o.detail=25;o.flow=2.5f;o.layers=6;o.allowed=allowed;
            o.smoothing=0;StyleKit.Result raw=OrganicFlowPortraitEngine.generate(photo,w,h,o);
            o.smoothing=100;StyleKit.Result smooth=OrganicFlowPortraitEngine.generate(photo,w,h,o);
            check(raw.layers.size()==smooth.layers.size(),"Lost physical layers");
            int pairs=0,mismatches=0,active=0,activeRetained=0;
            for(int layer=1;layer<raw.layers.size();layer++){
                boolean[] before=OrganicFlowCutSmoother.rasterize(raw.layers.get(layer).loops,surf.w,surf.h,surf.mm);
                boolean[] after=OrganicFlowCutSmoother.rasterize(smooth.layers.get(layer).loops,surf.w,surf.h,surf.mm);
                check(before!=null&&after!=null,"Invalid rasterized loop on layer "+layer);
                for(int y=0;y<surf.h;y++)for(int x=0;x<surf.w;x++){
                    float fx=(x+.5f)/surf.w,fy=(y+.5f)/surf.h;
                    // This includes both eyes, nose and mouth on the portrait fixture.
                    if(fx<.25f||fx>.75f||fy<.30f||fy>.75f)continue;
                    int idx=y*surf.w+x;
                    if(!surf.valid[idx])continue;
                    pairs++;
                    if(before[idx]!=after[idx])mismatches++;
                    if(before[idx]){active++;if(after[idx])activeRetained++;}
                }
            }
            double identity=1-mismatches/(double)Math.max(1,pairs);
            double retention=activeRetained/(double)Math.max(1,active);
            System.out.printf(java.util.Locale.US,"mode=%d detail=25 flow=2.5 face sheet identity=%.3f retained=%.3f nodes=%d/%d%n",
                mode,identity,retention,raw.totalPoints(),smooth.totalPoints());
            check(identity>.78,"Strong smoothing changed too much important face geometry for mode "+mode);
            check(retention>.76,"Strong smoothing erased facial color features for mode "+mode);
            check(Arrays.equals(raw.layers.get(0).loops.get(0),smooth.layers.get(0).loops.get(0)),
                "Base Snip changed");
        }
        System.out.println("PASS original portrait features retained at maximum smoothing");
    }
}

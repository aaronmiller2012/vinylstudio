package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M8: Confetti overlay pieces are placed manufacturably. On every overlay sheet two pieces either
 * overlap (merged by the union) or are at least one weed gap apart; a near miss left a sub-gap
 * waste sliver. Checked directly on the placed outlines, and on the final engine output: classified
 * cut findings of all six Confetti styles at 8 and 11 in stay far below the 566-569 engine
 * (astronaut: 701-4133 at 8 in, 1500-5218 at 11 in).
 */
public final class M8ConfettiClearanceTest {
    static int failures;
    static void check(boolean ok,String m){System.out.println((ok?"PASS ":"FAIL ")+m);if(!ok)failures++;}
    public static void main(String[] args)throws Exception{
        VectorBooleanEngine.install((VectorBooleanEngine.Backend)Class.forName("com.halftonestudio.JtsVectorBooleanBackend").getDeclaredConstructor().newInstance());
        M4ArtBench.Photo p=M4ArtBench.load(new java.io.File("tests/m4/photos/astronaut.png"),512);
        // 1. Placement invariant on real plans: no same-sheet near miss among placed outlines.
        for(int style:new int[]{VinylStyleSuite.CONFETTI,VinylStyleSuite.CONFETTI_ROUND,VinylStyleSuite.CONFETTI_FALLEN}){
            final float gap=.508f+FallenConfettiEngine.CLEARANCE_MARGIN_MM;
            FallenConfettiEngine.SheetClearance c=new FallenConfettiEngine.SheetClearance(gap);
            java.util.SplittableRandom rnd=new java.util.SplittableRandom(style);
            ArrayList<float[]> placed=new ArrayList<>();ArrayList<Integer> slots=new ArrayList<>();
            int offered=0;
            for(int i=0;i<4000;i++){
                float x=(float)rnd.nextDouble()*120,y=(float)rnd.nextDouble()*120,r=.6f+(float)rnd.nextDouble()*1.8f;
                int slot=1+rnd.nextInt(3);
                float[] poly=new float[16];for(int k=0;k<8;k++){double a=k*Math.PI/4+style;poly[2*k]=x+(float)(Math.cos(a)*r);poly[2*k+1]=y+(float)(Math.sin(a)*r*.8);}
                offered++;
                float[] out=c.place(slot,poly,r*.5f);
                if(out!=null){placed.add(out);slots.add(slot);}
            }
            int near=0;
            for(int i=0;i<placed.size();i++)for(int j=i+1;j<placed.size();j++){
                if(!slots.get(i).equals(slots.get(j)))continue;
                float[] a=placed.get(i),b=placed.get(j);
                float[] ba=FallenConfettiEngine.bounds(a),bb=FallenConfettiEngine.bounds(b);
                if(ba[0]>bb[2]+gap||bb[0]>ba[2]+gap||ba[1]>bb[3]+gap||bb[1]>ba[3]+gap)continue;
                if(FallenConfettiEngine.overlap(a,b))continue;
                if(FallenConfettiEngine.closest(a,b)[0]<.508f)near++;
            }
            check(near==0,String.format(Locale.US,"%s-seeded field: %d placed of %d, %d moved, %d not placed, 0 same-sheet near misses (%d)",
                VinylStyleSuite.NAMES[style],placed.size(),offered,c.nudged,c.dropped,near));
        }
        // 2. Engine output at production sizes.
        int[] styles={VinylStyleSuite.CONFETTI,VinylStyleSuite.CONFETTI_ROUND,VinylStyleSuite.CONFETTI_SQUARE,
            VinylStyleSuite.CONFETTI_PAPER,VinylStyleSuite.CONFETTI_STREAMER,VinylStyleSuite.CONFETTI_FALLEN};
        for(int style:styles)for(float size:new float[]{203.2f,279.4f}){
            StyleKit.Result r=VinylStyleSuite.generate(p.argb,p.w,p.h,M4ArtBench.suiteConfig(style,p,size,.254f,.508f));
            int found=0;
            for(int li=0;li<r.layers.size();li++){
                StyleKit.Result one=new StyleKit.Result(r.widthMm,r.heightMm);one.layers.add(r.layers.get(li));
                found+=StyleCutGeometryPreflight.inspect(one,.254f,.508f,100000).size();
            }
            int bound=size<250?300:450;
            check(found<=bound,String.format(Locale.US,"%s %.0f in: %d classified findings (bound %d; 566-569 engine: hundreds to thousands)",
                VinylStyleSuite.NAMES[style],size/25.4,found,bound));
        }
        if(failures>0){System.out.println(failures+" M8 Confetti clearance check(s) FAILED");System.exit(1);}
        System.out.println("PASS M8 Confetti sheet clearance test");
    }
}

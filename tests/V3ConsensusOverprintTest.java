package com.halftonestudio;

import java.util.Arrays;
import java.util.Random;

/** Research follow-up: no stale swatch cache, lightness-sensitive matching,
 * transparent/Snip isolation, exported physical sheet consistency and geometry
 * stability when only the color optimizer's computation is accelerated. */
public final class V3ConsensusOverprintTest {
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static float[] square(float a,float b,float c,float d){
        return new float[]{a,b,c,b,c,d,a,d};
    }
    private static void preparedParity(){
        Random random=new Random(0x14a7e);
        for(int trial=0;trial<80;trial++){
            int w=40+(trial%5)*8,h=43+(trial%7)*5;
            int[] px=new int[w*h];boolean[] allowed=new boolean[px.length];
            for(int i=0;i<px.length;i++){
                px[i]=(random.nextInt(5)==0?0:0xff000000)|random.nextInt(0x1000000);
                allowed[i]=random.nextInt(4)!=0;
            }
            int[] colors={0xff262322,0xffc78676,0xffdfceae,0xff47647b,
                          0xff6d503e,0xffc7b14f,0xff78885c,0xffefe8df};
            int count=2+trial%7;
            float[][] marks={square(1,1,w*.65f,h*.82f),
                square(w*.35f,h*.10f,w-1,h-1),
                trial%3==0?null:square(w*.2f,h*.35f,w*.80f,h*.92f)};
            int[] original=colors.clone();
            int[] direct=OpaqueOverlaySwatchPlanner.choose(px,allowed,w,h,0,0,w,h,
                    marks,colors,count,.7f);
            OpaqueOverlaySwatchPlanner.PreparedPalette prepared=
                    new OpaqueOverlaySwatchPlanner.PreparedPalette(colors,count);
            int[] reused=OpaqueOverlaySwatchPlanner.choose(px,allowed,w,h,0,0,w,h,
                    marks,prepared,.7f);
            require(Arrays.equals(direct,reused),"Prepared palette deviated at trial "+trial);
            require(Arrays.equals(colors,original),"Input swatches mutated");
            for(int i=0;i<reused.length;i++)require(reused[i]>=0&&reused[i]<count,"Bad physical layer");
            colors[1]^=0x00ffffff;
            int[] modified=OpaqueOverlaySwatchPlanner.choose(px,allowed,w,h,0,0,w,h,
                    marks,colors,count,.7f);
            OpaqueOverlaySwatchPlanner.PreparedPalette edited=
                    new OpaqueOverlaySwatchPlanner.PreparedPalette(colors,count);
            require(Arrays.equals(modified,OpaqueOverlaySwatchPlanner.choose(px,allowed,w,h,
                    0,0,w,h,marks,edited,.7f)),"Manual edit used stale swatch snapshot");
        }
    }
    private static void tonalPrioritization(){
        double[] requested={.20,.005,.005};
        double[] wrongLightness={.38,.005,.005};
        double[] wrongChroma={.20,.20,.15};
        require(OpaqueOverlaySwatchPlanner.error(requested,wrongChroma)>
                OpaqueOverlaySwatchPlanner.error(requested,wrongLightness),
                "Chroma/luminance weighting unstable");
        // Lightness is deliberately more important than an equivalent single
        // chromatic coordinate deviation, while remaining a finite metric.
        require(OpaqueOverlaySwatchPlanner.error(requested,wrongLightness)>
                .18*.18,"Overprint lost portrait lightness priority");
    }
    private static void adaptiveBase(){
        int[] stock={0xff16181a,0xff474445,0xff787170,0xffa99e96,
                     0xffd5b9a6,0xffe7d4bd,0xffc88870,0xfff8eee1};
        int[] dark=new int[480],light=new int[480];
        Arrays.fill(dark,0xff3e3935);Arrays.fill(light,0xffd4c1b0);
        int[] cool=OpaqueOverlayPhotoPalette.arrange(stock,4,dark);
        int[] warm=OpaqueOverlayPhotoPalette.arrange(stock,4,light);
        double d=PerceptualVinylColor.lab(cool[0])[0];
        double l=PerceptualVinylColor.lab(warm[0])[0];
        require(l>d+.12,"Bright portrait picked the same needlessly dark foundation: "+d+" / "+l);
        int[] empty=new int[100]; // alpha zero must not supply a visible swatch
        require(OpaqueOverlayPhotoPalette.arrange(stock,4,empty).length==8,"Empty photo fallback");
        int[] stockCopy=stock.clone();
        Arrays.sort(stockCopy);Arrays.sort(warm);
        require(Arrays.equals(stockCopy,warm),"Optimizer fabricated physical colors");
    }
    public static void main(String[] args){
        preparedParity();tonalPrioritization();adaptiveBase();
        System.out.println("PASS Consensus overprint: prepared-cache parity, edit isolation, tonal fidelity, adaptive photo foundation");
    }
}

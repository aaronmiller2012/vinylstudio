package com.halftonestudio;

import java.util.Arrays;
import java.util.Collections;

/** Host-side tests for CMYK physical order, grayscale, nondestructive tonal preprocessing. */
public final class EdgeLevelsStackTest {
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private static int[] gradient(int w,int h){
        int[] x=new int[w*h];for(int j=0;j<h;j++)for(int i=0;i<w;i++){
            int v=Math.max(0,Math.min(255,Math.round((i*255f)/(w-1))));
            x[j*w+i]=0xff000000|(v<<16)|(v<<8)|v;
        }return x;
    }
    private static void grayscale(){
        for(int layers=2;layers<=8;layers++){
            int[] palette=VinylPalettes.get(7,layers);
            for(int c:palette){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
                check(r==g&&g==b,"grayscale layer is colored at layer count "+layers);
            }
            for(int j=1;j<palette.length;j++)check(palette[j]!=palette[j-1],"duplicate gray layers");
        }
    }
    private static void stack(){
        int[] src=gradient(28,20);
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.widthMm=82;s.minimumMm=.4f;
        Collections.reverse(s.layers);
        VinylEngine.Result result=VinylEngine.generate(src,28,20,s);
        int[] correct={2,0,1,3};
        for(int i=0;i<4;i++)check(result.settings.layers.get(i).cmykChannel==correct[i],"CMYK stack order incorrect at "+i);
        check(result.settings.layers.get(0).color==0xffffdd00,"yellow must be bottom");
        check(result.settings.layers.get(3).color==0xff131313,"black must be top");
        check(s.layers.get(0).cmykChannel==3,"generate changed the source setting rather than copying it");
    }
    private static void levels(){
        int w=40,h=22;int[] src=gradient(w,h);
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        check(s.edgePriority>.01f,"new projects must emphasize source edge definition");
        s.edgePriority=0f;check(ImageLevels.isNeutral(s),"explicitly reset levels must be neutral");
        check(ImageLevels.apply(src,w,h,s)==src,"neutral operation should not clone entire source");
        s.exposureStops=.6f;s.photoContrast=1.25f;
        s.levelsBlack=28;s.levelsWhite=225;s.levelsGamma=1.35f;s.edgePriority=.75f;
        int[] original=src.clone();int[] output=ImageLevels.apply(src,w,h,s);
        check(!Arrays.equals(output,src),"levels had no tonal effect");
        check(Arrays.equals(src,original),"levels mutated source");
        check(output!=src,"levels must be non-destructive array");
        int[] auto=ImageLevels.autoPoints(src,w,h,s);
        check(auto[0]>=0&&auto[1]<=255&&auto[0]<auto[1],"bad auto levels");
        // Keep only the left 25%; test pixels outside saved snips never change.
        s.snips.add(new SnipMask.Operation(SnipMask.KEEP_INSIDE,0f,0f,.25f,0f,.25f,1f,0f,1f));
        int[] masked=ImageLevels.apply(src,w,h,s);
        for(int y=0;y<h;y++)for(int x=15;x<w;x++)check(masked[y*w+x]==src[y*w+x],"changed a removed Snip region");
        check(s.copy().levelsBlack==s.levelsBlack&&s.copy().edgePriority==s.edgePriority,"levels not retained in Settings.copy");
        check(s.copy().exposureStops==s.exposureStops&&s.copy().photoContrast==s.photoContrast,"light controls not copied");
        VinylEngine.Settings single=VinylEngine.Settings.defaults();single.edgePriority=0;
        single.exposureStops=1.0f;
        int[] bright=ImageLevels.apply(src,w,h,single);
        check(bright[8*w+12]!=src[8*w+12],"Exposure slider should change pixel data");
        single.exposureStops=0;single.photoContrast=1.5f;
        check(!Arrays.equals(src,ImageLevels.apply(src,w,h,single)),"Contrast slider has no effect");
        single.photoContrast=1f;single.levelsShadows=.6f;
        check(!Arrays.equals(src,ImageLevels.apply(src,w,h,single)),"Shadows slider has no effect");
    }
    public static void main(String[] args){grayscale();stack();levels();
        System.out.println("PASS: neutral 2-8 grayscale; locked Y-C-M-K; non-destructive post-snip levels; auto points; settings copy");
    }
}

package com.halftonestudio;
import java.util.*;
public final class V3OrganicMorphologyParityTest {
    static boolean[] oldMorph(boolean[] mask,int w,int h,int radius,boolean dilation){
        int size=2*radius+1;
        int[] ox=new int[size*size],oy=new int[size*size];int offsets=0;
        for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++)
            if(dx*dx+dy*dy<=radius*radius){ox[offsets]=dx;oy[offsets]=dy;offsets++;}
        boolean[] result=new boolean[mask.length];
        for(int y=0;y<h;y++){
            for(int x=0;x<w;x++){
                boolean value=!dilation;
                for(int k=0;k<offsets;k++){
                    int nx=x+ox[k],ny=y+oy[k];
                    boolean inside=nx>=0&&nx<w&&ny>=0&&ny<h&&mask[ny*w+nx];
                    if(dilation&&inside){value=true;break;}
                    if(!dilation&&!inside){value=false;break;}
                }
                result[y*w+x]=value;
            }
        }
        return result;
    }
    public static void main(String[] args){
        Random rng=new Random(81571);
        for(float width:new float[]{76.2f,101.6f,279.4f}){
            int w=63,h=81,slots=7;
            int[] pixels=new int[w*h];Arrays.fill(pixels,0xff848484);
            StyleKit.Source src=new StyleKit.Source(pixels,w,h,null,width);
            PatternPortraitTrioEngine.Surface s=new PatternPortraitTrioEngine.Surface(src,slots,.8f);
            boolean[][] old=new boolean[slots][];
            for(int l=1;l<slots;l++){
                boolean[] mask=s.ink[l];
                for(int i=0;i<mask.length;i++)mask[i]=rng.nextInt(5)<3;
                old[l]=mask.clone();
            }
            OrganicFlowPortraitEngine.conditionMasks(s,.8f,.5f);
            int close=Math.max(1,(int)Math.ceil((.5f*.50f+.07f)/s.mm));
            int open=Math.max(1,(int)Math.ceil((.8f*.50f+.08f)/s.mm));
            for(int l=1;l<slots;l++){
                boolean[] a=oldMorph(old[l],s.w,s.h,close,true);
                boolean[] b=oldMorph(a,s.w,s.h,close,false);
                a=oldMorph(b,s.w,s.h,open,false);
                b=oldMorph(a,s.w,s.h,open,true);
                for(int i=0;i<b.length;i++)b[i]&=s.valid[i];
                if(!Arrays.equals(s.ink[l],b))throw new AssertionError("Different morphology at "+width+" layer "+l);
            }
        }
        System.out.println("PASS optimized morphology is bit-for-bit identical for 3/4/11-inch canvases");
    }
}

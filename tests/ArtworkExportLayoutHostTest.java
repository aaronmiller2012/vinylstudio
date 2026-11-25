package com.halftonestudio;

/** Physical sizing and bounded raster allocation checks: no Android dependencies. */
public final class ArtworkExportLayoutHostTest {
    private static void test(boolean b,String s){if(!b)throw new AssertionError(s);}
    private static void rejected(float w,float h){
        try { ArtworkExportLayout.pngPixels(w,h);throw new AssertionError("Accepted invalid size"); }
        catch(IllegalArgumentException expected){ }
    }
    public static void main(String[] args){
        int[] letter=ArtworkExportLayout.pdfPoints(152.4f,228.6f);
        test(Math.abs(letter[0]-432)<=1&&Math.abs(letter[1]-648)<=1,"PDF physical inches incorrect");
        int[] a=ArtworkExportLayout.pngPixels(152.4f,228.6f);
        test(Math.abs(a[0]-1800)<=1&&Math.abs(a[1]-2700)<=1,"300 dpi raster sizing incorrect");
        for(float w:new float[]{25f,100f,152.4f,300f,1000f,3000f}){
            for(float h:new float[]{25f,100f,152.4f,500f,1200f,3000f}){
                int[] size=ArtworkExportLayout.pngPixels(w,h);
                test(size[0]>0&&size[1]>0,"Nonpositive PNG dimensions");
                test(size[0]<=4096&&size[1]<=4096&&
                    ((long)size[0])*size[1]<=ArtworkExportLayout.PNG_MAX_PIXELS,"Memory cap exceeded");
                float ratio=size[0]/(float)size[1];
                test(Math.abs(ratio/(w/h)-1f)<.015f,"Aspect ratio changed");
            }
        }
        rejected(Float.NaN,100);rejected(0,100);rejected(-10,10);
        rejected(Float.POSITIVE_INFINITY,100);rejected(4000,100);
        try{ ArtworkExportLayout.pdfPoints(-1,100);throw new AssertionError("Invalid PDF size accepted"); }
        catch(IllegalArgumentException expected){ }
        System.out.println("PASS artwork export physical PDF size, PNG 300dpi, bounds, aspect ratio");
    }
}

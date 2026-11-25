package com.halftonestudio;
import java.lang.reflect.Method;
public final class FishScaleClearanceRC132Test {
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args) throws Exception{
        Method shape=LayeredCellPortraitEngine.class.getDeclaredMethod("fishScaleShape",
            float.class,float.class,int.class,float.class,float.class,float.class,float.class,float.class,float.class);
        shape.setAccessible(true);
        float pitch=200f/18f, rowPitch=pitch*.69f,amp=rowPitch*.76f,gap=.2f;
        float min=Float.POSITIVE_INFINITY;
        for(int row=1;row<5;row++){
            float shift=(row&1)!=0?pitch*.5f:0;
            float prevShift=((row-1)&1)!=0?pitch*.5f:0;
            float nextShift=((row+1)&1)!=0?pitch*.5f:0;
            for(int col=2;col<=8;col++){
                float left=col*pitch+shift;
                float[] a=(float[])shape.invoke(null,left,left+pitch,row,rowPitch,pitch,shift,prevShift,amp,gap);
                for(int nc=col-1;nc<=col+1;nc++){
                    float nl=nc*pitch+nextShift;
                    float[] b=(float[])shape.invoke(null,nl,nl+pitch,row+1,rowPitch,pitch,nextShift,shift,amp,gap);
                    float d=PhysicalContourClearance.minDistance(a,b);
                    min=Math.min(min,d);
                }
            }
        }
        require(min>=gap-.004f,"normal weed gap: "+min+" < "+gap);
        // Verify actual masked cut pieces and the real export gate (not only
        // the analytic continuous boundary). Count/size must remain practical.
        int w=140,h=160;
        int[] pixels=new int[w*h];java.util.Arrays.fill(pixels,0xff7b5347);
        LayeredCellPortraitEngine.Options opt=new LayeredCellPortraitEngine.Options();
        opt.widthMm=200;opt.columns=18;opt.layerCount=8;
        opt.photoDetail=1;opt.gapMm=.20f;opt.minFeatureMm=.254f;
        opt.detailMotifOverlay=false;opt.registration=false;
        boolean[] mask=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            mask[y*w+x]=Math.pow((x-w*.5)/(.46*w),2)+Math.pow((y-h*.5)/(.46*h),2)<1;
        opt.allowedPixels=mask;
        LayeredCellPortraitEngine.Result result=FishScaleMosaicEngine.generate(pixels,w,h,opt);
        for(int sheet=0;sheet<opt.layerCount;sheet++)if(result.pieceCounts[sheet]>0){
            String svg=result.svg(sheet);
            LayeredCellExportSafety.validate(result,sheet,svg);
        }
        System.out.println("PASS RC132 normal-offset scallops, masked SVG and segment clearance: "+min+" mm >= "+gap+" mm");
    }
}

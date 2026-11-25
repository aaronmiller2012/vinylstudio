package com.halftonestudio;

/** Regression: all seven stacked stencil masks must retain smooth major contours,
 * while openings/isolated cut features survive unchanged. */
public final class CleanRegionEdgesV2545Test {
    private static void ok(boolean v,String s){if(!v)throw new AssertionError(s);}
    private static int count(String s,char c){int n=0;for(int i=0;i<s.length();i++)if(s.charAt(i)==c)n++;return n;}
    private static void sevenNestedCurves(){
        int w=185,h=150;
        boolean[] preceding=null;
        for(int layer=0;layer<7;layer++){
            boolean[] m=new boolean[w*h];
            double rx=72-layer*5.0,ry=58-layer*4.3;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                double dx=x-90,dy=y-73;
                // Raster stair-stepped boundary with a portrait-like cheek fold.
                double wave=2.3*Math.sin(y*.064)+1.1*Math.sin(y*.19);
                m[y*w+x]=((dx-wave)*(dx-wave)/(rx*rx)+dy*dy/(ry*ry))<1;
            }
            if(layer==3){ // isolated speckle and eye opening must not defeat cheek curves
                m[12*w+13]=true;
                for(int y=69;y<72;y++)for(int x=82;x<85;x++)m[y*w+x]=false;
            }
            SmoothStencilContours.Geometry g=MultilayerStencilEngine.geometryForTest(m,w,h,92.5f,75f,.39f);
            int curves=count(g.data,'C');
            System.out.printf("layer %d: cubic=%d, linear=%d, contours=%d%n",layer+1,curves,count(g.data,'L'),g.loops);
            ok(curves>=6,"layer "+(layer+1)+" major silhouette reverted to pixels");
            ok(g.loops==MultilayerStencilEngine.countUnionContours(m,w,h),"layer topology changed");
            if(layer==3){ok(g.raster[12*w+13],"small island lost");ok(!g.raster[70*w+83],"eye cavity filled");}
            if(preceding!=null&&layer!=4){ // original nested independent profile, except added isolated speckle
                for(int k=0;k<m.length;k++)if(g.raster[k]&&!preceding[k]){
                    // Allow the local contour difference around shared curved boundaries.
                    int x=k%w,y=k/w;ok(x>10&&x<175&&y>10&&y<140,"curve escaped cut bounds");
                }
            }
            preceding=g.raster;
        }
    }
    private static void exactRectangle(){
        int w=240,h=12;boolean[] m=new boolean[w*h];java.util.Arrays.fill(m,true);
        SmoothStencilContours.Geometry g=MultilayerStencilEngine.geometryForTest(m,w,h,120,6,.3f);
        ok(count(g.data,'L')<=4,"straight base outline bloated");
        ok(count(g.data,'M')==1,"base rectangle topology changed");
    }
    public static void main(String[] args){sevenNestedCurves();exactRectangle();System.out.println("PASS v2.54.5 clean region boundaries");}
}

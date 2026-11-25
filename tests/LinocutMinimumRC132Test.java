package com.halftonestudio;
public final class LinocutMinimumRC132Test {
    private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    private static double dist(float[] xy,int a,int b){
        return Math.hypot(xy[a*2]-xy[b*2],xy[a*2+1]-xy[b*2+1]);
    }
    public static void main(String[] args){
        final int w=100,h=120;int[] argb=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=20+(int)(190*(.25+0.75*x/(float)(w-1))*y/h);
            argb[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        LinocutEngine.Options o=new LinocutEngine.Options();
        o.widthMm=180;o.pitchMm=1.35f;o.minWidthMm=.40f;o.minLengthMm=2.0f;
        o.crossPass=true;
        StyleKit.Result result=LinocutEngine.generate(argb,w,h,o);
        int count=0;
        for(StyleKit.Layer layer:result.layers)for(float[] loop:layer.loops){
            require(loop.length>=8 && loop.length%4==0,"Malformed ribbon");
            int n=loop.length/4;
            // StyleKit.ribbon returns the left bank and the reversed right bank.
            double a=dist(loop,0,2*n-1),b=dist(loop,n-1,n);
            require(a>=o.minWidthMm-.004,"Beginning of ribbon below physical width "+a);
            require(b>=o.minWidthMm-.004,"End of ribbon below physical width "+b);
            require(StyleKit.selfIntersections(loop,1)==0,"Self-intersecting ribbon");
            count++;
        }
        require(count>30,"Linocut detail unexpectedly missing");
        System.out.println("PASS RC132 linocut endpoint widths >= "+o.minWidthMm+"mm: "+count+" ribbons");
    }
}

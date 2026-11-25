package com.halftonestudio;
import java.util.*;
/** Host-side validations for actual cut contour spacing, texture recovery, counts, and safety review. */
public final class StainedGlassCutFixRC80Test {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static int[] gray(int w,int h,int v){int[] a=new int[w*h];Arrays.fill(a,0xff000000|(v<<16)|(v<<8)|v);return a;}
    static int loops(StyleKit.Result r){int n=0;for(StyleKit.Layer layer:r.layers)if(!layer.name.equals("lead"))n+=layer.loops.size();return n;}
    static float separation(int[] owners,float width,float lead){
        int side=320;StyleKit.Source src=new StyleKit.Source(gray(side,side,128),side,side,null,width);
        StainedGlassEngine.Pixels pix=new StainedGlassEngine.Pixels(src);
        StainedGlassEngine.Options opt=new StainedGlassEngine.Options();opt.widthMm=width;opt.leadMm=lead;opt.minPieceAreaMm2=.3f;
        StyleKit.Layer[] glass={new StyleKit.Layer("l",0xff000000),new StyleKit.Layer("r",0xffffffff)};
        StainedGlassEngine.extractPanes(pix,owners,null,new int[]{0,1},glass,opt);
        check(!glass[0].loops.isEmpty()&&!glass[1].loops.isEmpty(),"lead erased a half-pane");
        float a=Float.NEGATIVE_INFINITY,b=Float.POSITIVE_INFINITY,mid=width*.5f;
        for(float[] poly:glass[0].loops)for(int k=0;k<poly.length;k+=2){
            int next=(k+2)%poly.length;float y1=poly[k+1],y2=poly[next+1];
            if((y1<=mid&&mid<=y2)||(y2<=mid&&mid<=y1)){
                float t=y1==y2?0:(mid-y1)/(y2-y1);
                a=Math.max(a,poly[k]+t*(poly[next]-poly[k]));
            }
        }
        for(float[] poly:glass[1].loops)for(int k=0;k<poly.length;k+=2){
            int next=(k+2)%poly.length;float y1=poly[k+1],y2=poly[next+1];
            if((y1<=mid&&mid<=y2)||(y2<=mid&&mid<=y1)){
                float t=y1==y2?0:(mid-y1)/(y2-y1);
                b=Math.min(b,poly[k]+t*(poly[next]-poly[k]));
            }
        }
        check(Float.isFinite(a)&&Float.isFinite(b),"could not find lead boundary at sheet midpoint");
        return b-a;
    }
    static void lead(){
        for(float width:new float[]{76.2f,152.4f,254f}){
            int[] owner=new int[320*320];for(int y=0;y<320;y++)for(int x=0;x<320;x++)owner[y*320+x]=x<160?0:1;
            for(float mm:new float[]{.6f,.7f,1.4f,3f}){
                float actual=separation(owner,width,mm);
                // Max 0.15 mm absolute error across artboard sizes, even
                // when the requested lead is narrower than a coarse pixel.
                check(Math.abs(actual-mm)<.15f,"lead width "+width+" mm / "+mm+" mm became "+actual+" mm");
            }
        }
    }
    static void texture(){
        int side=180;int[] pixels=new int[side*side];
        for(int y=0;y<side;y++)for(int x=0;x<side;x++){
            int v=((x/4+y/4)%2==0)?20:240;
            pixels[y*side+x]=0xff000000|v<<16|v<<8|v;
        }
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=VinylStyleSuite.GLASS;c.detail=100;c.colorCount=5;
        StyleKit.Result r=VinylStyleSuite.generate(pixels,side,side,c);
        check(loops(r)>10,"high texture returned a lead-only artwork");
        check(loops(r)<=1160,"high texture exceeds actual physical contour budget");
    }
    static void complexity(){
        int side=130;int[] pixels=new int[side*side];Random random=new Random(30);
        for(int i=0;i<pixels.length;i++){int v=random.nextInt(256);pixels[i]=0xff000000|v<<16|v<<8|v;}
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=VinylStyleSuite.GLASS;c.detail=0;c.colorCount=5;
        StyleKit.Result r=VinylStyleSuite.generate(pixels,side,side,c);
        check(loops(r)>0&&loops(r)<=160,"physical contour count not bounded: "+loops(r));
    }
    static void cutSafety(){
        StyleKit.Result r=new StyleKit.Result(20,20);StyleKit.Layer layer=new StyleKit.Layer("glass",0xffaa8844);
        layer.loops.add(new float[]{0,0,4,0,4,2.95f,6,2.95f,6,0,10,0,10,6,6,6,6,3.05f,4,3.05f,4,6,0,6});
        r.layers.add(layer);
        check(!StyleCutGeometryPreflight.inspect(r,.6f,100).isEmpty(),"failed to detect a 0.10mm throat");
        StyleKit.Result safe=new StyleKit.Result(20,20);StyleKit.Layer ok=new StyleKit.Layer("safe",0xffaa8844);
        ok.loops.add(new float[]{0,0,6,0,6,6,0,6});safe.layers.add(ok);
        check(StyleCutGeometryPreflight.inspect(safe,.6f,100).isEmpty(),"safe square generated false warning");
    }
    static void tone(){
        int w=160,h=160;int[] photo=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=(x*255/(w-1)+y*255/(h-1))/2;
            photo[y*w+x]=0xff000000|v<<16|v<<8|v;
        }
        StainedGlassEngine.Options o=new StainedGlassEngine.Options();o.maxPieces=150;
        StyleKit.Result first=StainedGlassEngine.generate(photo,w,h,o);
        o.gamma=.35f;o.contrast=2.5f;
        StyleKit.Result second=StainedGlassEngine.generate(photo,w,h,o);
        long digest1=hash(first),digest2=hash(second);
        check(digest1!=digest2,"gamma and contrast still inert in glass engine");
    }
    static long hash(StyleKit.Result r){long h=1237;for(StyleKit.Layer l:r.layers)for(float[] p:l.loops)for(float v:p)h=31*h+Float.floatToIntBits(v);return h;}
    static void staleCount(){
        int slots=NewStyleColorSlots.slotCount(VinylStyleSuite.GLASS,0,2);
        check(slots==3,"bad color slot fallback");
        VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=VinylStyleSuite.GLASS;c.colorCount=2;
        StyleKit.Result r=VinylStyleSuite.generate(gray(90,90,120),90,90,c);
        check(!r.layers.isEmpty()&&loops(r)>0,"restored legacy two-slot GLASS is not renderable");
    }
    public static void main(String[] a){
        lead();texture();complexity();cutSafety();tone();staleCount();
        System.out.println("PASS RC80 physical lead gaps (3 sizes, 4 thicknesses), photo texture, geometry cap, local neck, tone, stale slots");
    }
}

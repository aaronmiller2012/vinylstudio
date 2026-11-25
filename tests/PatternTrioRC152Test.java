package com.halftonestudio;

/** JVM-only smoke, physically sized output and deterministic cut-geometry checks. */
public final class PatternTrioRC152Test {
    static final int W=160,H=204;
    static int[] image(){
        int[] rgb=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int r=Math.max(0,Math.min(255,100+x*110/W+y*40/H));
            int g=Math.max(0,Math.min(255,40+x*65/W+y*90/H));
            int b=Math.max(0,Math.min(255,40+(W-x)*70/W+y*25/H));
            if(x>50&&x<76&&y>80&&y<101)r=g=b=15;
            if(x>95&&x<120&&y>80&&y<101)r=g=b=15;
            rgb[y*W+x]=0xff000000|(r<<16)|(g<<8)|b;
        }return rgb;
    }
    static void check(boolean ok,String error){if(!ok)throw new AssertionError(error);}
    public static void main(String[] args){
        int[] pixels=image();
        for(int type=0;type<3;type++){
            PatternPortraitTrioEngine.Options o=new PatternPortraitTrioEngine.Options();
            o.kind=type;o.detail=50;o.widthMm=152.4f;o.minCutMm=.254f;
            o.layers=6;o.spacingMm=.5f;
            o.allowed=new boolean[pixels.length];
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){
                float dx=(x-W*.5f)/(W*.46f),dy=(y-H*.5f)/(H*.48f);
                o.allowed[y*W+x]=(dx*dx+dy*dy<=1f)&&!(x>70&&x<82&&y>108&&y<130);
            }
            StyleKit.Result a=PatternPortraitTrioEngine.generate(pixels,W,H,o);
            System.out.println((type==0?"confetti":type==1?"chainmail":"spider-web")+
                " sheets="+a.layers.size()+" points="+a.totalPoints()+" summary="+a.summary);
            check(a.layers.size()==(type==2?1:6),"physical layer count");
            check(a.totalPoints()>200,"no geometry");
            check(Math.abs(a.widthMm-o.widthMm)<.01f,"physical width");
            check(a.summary.contains("element"),"summary");
            int loops=0;
            for(StyleKit.Layer l:a.layers){
                check(l.lines.isEmpty(),"non-cuttable pen lines");
                for(float[] poly:l.loops){
                    check(poly.length>=6&&(poly.length&1)==0,"invalid polygon length");
                    for(int q=0;q<poly.length;q+=2){
                        check(Float.isFinite(poly[q])&&Float.isFinite(poly[q+1]),"nonfinite");
                        check(poly[q]>=-.05f&&poly[q]<=a.widthMm+.05f,"x clipped");
                        check(poly[q+1]>=-.05f&&poly[q+1]<=a.heightMm+.05f,"y clipped");
                    }
                    loops++;
                }
            }
            check(loops>3,"no visible contours");
            // Modifying geometry controls must alter an engine's generated geometry.
            o.detail=80;o.spacingMm=.9f;o.organic=85;
            StyleKit.Result b=PatternPortraitTrioEngine.generate(pixels,W,H,o);
            check(b.totalPoints()!=a.totalPoints()||!b.summary.equals(a.summary),"inert controls");
        }
        // Test every supported physical layer extreme, including manual palettes.
        for(int type=0;type<2;type++)for(int count:new int[]{2,8}){
            PatternPortraitTrioEngine.Options edge=new PatternPortraitTrioEngine.Options();
            edge.kind=type;edge.detail=25;edge.layers=count;edge.widthMm=152.4f;
            edge.palette=new int[count];
            for(int i=0;i<count;i++)edge.palette[i]=0xff000000|((40+i*23)<<16)|((60+i*19)<<8)|(45+i*19);
            StyleKit.Result result=PatternPortraitTrioEngine.generate(pixels,W,H,edge);
            check(result.layers.size()==count,"manual layer cap");
            for(int i=0;i<count;i++)check(result.layers.get(i).color==edge.palette[i],"changed manual palette");
        }
        System.out.println("PASS RC152 three styles, physical slots, clipped SVG contours and controls");
    }
}

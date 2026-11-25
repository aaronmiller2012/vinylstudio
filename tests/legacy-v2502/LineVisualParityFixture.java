package com.halftonestudio;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Locked SVG geometry signatures of all 12 independent line families, using v2.50.0. */
public final class LineVisualParityFixture {
    private static final int[] STYLES = {VinylEngine.LINE,VinylEngine.WAVE,VinylEngine.HATCH,
        VinylEngine.STRAIGHT,VinylEngine.CONTOUR,VinylEngine.CROSSHATCH,VinylEngine.RINGS,
        VinylEngine.RAYS,VinylEngine.ZIGZAG,VinylEngine.BROKEN,VinylEngine.BEADED,VinylEngine.FADE};

    static int[] portrait(int w,int h,int paper){
        int[] image = new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int rr,gg,bb;
            if(y<10 || x<8 || x>=w-7){image[y*w+x]=paper;continue;}
            if(x<21){rr=213;gg=139;bb=178;}
            else if(x<34){rr=85;gg=65;bb=78;}
            else if(x<46){rr=165;gg=161;bb=158;}
            else if(x<58){rr=225;gg=186;bb=161;}
            else {rr=46;gg=41;bb=46;}
            int subtle=(x*3+y*5)%15-7;
            rr=Math.min(255,Math.max(0,rr+subtle));
            gg=Math.min(255,Math.max(0,gg+subtle));
            bb=Math.min(255,Math.max(0,bb+subtle));
            if(y>=33&&y<39 && x>20 && x<60){rr=249;gg=249;bb=249;}
            if(y>=54 && y<58 && x>20 && x<60){rr=255;gg=255;bb=255;}
            image[y*w+x]=0xff000000|(rr<<16)|(gg<<8)|bb;
        }
        return image;
    }
    static String digest(String value) throws Exception {
        byte[] raw = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder();
        for(byte x:raw)b.append(String.format(java.util.Locale.US,"%02x",x&255));
        return b.toString();
    }
    public static void main(String[] args) throws Exception {
        VinylEngine.Settings base=VinylEngine.Settings.defaults();
        base.widthMm=71f; base.marginMm=2f; base.registration=false;
        base.masterSpacingMm=3.1f;base.minimumMm=.8f;base.bridgeMm=.5f;
        base.detailBoost=.3f; base.shapeDefinition=.72f;
        final int w=77,h=81;
        int[] image=portrait(w,h,base.paper);
        for(int variant=0;variant<3;variant++){
            for(int style:STYLES){
                VinylEngine.Settings s=base.copy();
                s.pattern=style;
                if(variant==1){s.portraitMidtones=0;s.shapeDefinition=0;s.detailBoost=0;}
                if(variant==2){s.lineInvert=true;s.portraitMidtones=.5f;}
                VinylEngine.Result r=VinylEngine.generate(image,w,h,s);
                for(int c=0;c<r.settings.layers.size();c++){
                    VinylEngine.Layer l=r.settings.layers.get(c);
                    String svg=VinylEngine.svg(r,c);
                    System.out.println(variant+" "+style+" "+l.cmykChannel+" "+r.shapes.get(c).size()+" "+digest(svg));
                }
            }
        }
    }
}

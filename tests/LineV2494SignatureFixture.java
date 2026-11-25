package com.halftonestudio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Frozen, independently generated with the user's installed/working v2.49.4 renderer.
 * 12 line types x 4 physical colors x 3 settings sets x 2 portraits = 288 checks.
 * Never regenerate the expected file from a candidate renderer.
 */
public final class LineV2494SignatureFixture {
    private static final int[] STYLES={VinylEngine.LINE,VinylEngine.WAVE,VinylEngine.HATCH,
        VinylEngine.STRAIGHT,VinylEngine.CONTOUR,VinylEngine.CROSSHATCH,VinylEngine.RINGS,
        VinylEngine.RAYS,VinylEngine.ZIGZAG,VinylEngine.BROKEN,VinylEngine.BEADED,VinylEngine.FADE};
    static int[] photo(int w,int h,int fixture,int paper){
        int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            if(y<10 || x<7 || x>=w-7){p[y*w+x]=paper;continue;}
            int r,g,b;
            if(fixture==0){
                if(x<19){r=220;g=88;b=145;}
                else if(x<32){r=39;g=37;b=42;}
                else if(x<45){r=112;g=156;b=188;}
                else if(x<59){r=224;g=189;b=156;}
                else {r=70;g=53;b=63;}
                int n=(x*3+y*5)%19-9;
                r=clamp(r+n);g=clamp(g+n);b=clamp(b+n);
            }else{
                // Distinct spatial frequency and independent CMYK contributors.
                int band=(x/12+y/17)%6;
                int[] colors={0xffef2b83,0xff0aa5e4,0xfff5ce1b,0xff161820,0xffb08363,0xffa6b9c4};
                int q=colors[band];r=(q>>16)&255;g=(q>>8)&255;b=q&255;
                if(x>20 && x<64 && y>23 && y<49){
                    float shade=(float)(Math.sin(x*.35)+Math.cos(y*.27));
                    r=clamp(r+(int)(shade*17));g=clamp(g+(int)(shade*17));b=clamp(b+(int)(shade*17));
                }
            }
            if(y>=35&&y<40 && x>19 && x<61){r=251;g=251;b=251;}
            if(y>=57&&y<60 && x>19 && x<61){r=255;g=255;b=255;}
            p[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return p;
    }
    private static int clamp(int x){return Math.max(0,Math.min(255,x));}
    private static String sha(String s)throws Exception{
        byte[] a=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder();for(byte x:a)b.append(String.format(Locale.US,"%02x",x&255));return b.toString();
    }
    public static void main(String[] a)throws Exception{
        VinylEngine.Settings baseline=VinylEngine.Settings.defaults();
        // Frozen v2.49.4 configuration: deliberately independent of new
        // user-facing defaults. This verifies a 0-recovery compatibility mode.
        baseline.blackColorProtection=.18f;
        baseline.shadowColorRecovery=.48f;
        baseline.portraitMidtones=.82f;
        baseline.lineContinuity=0f;
        baseline.linePresenceRecovery=0f;baseline.portraitBackgroundGuard=0f;
        for(VinylEngine.Layer layer:baseline.layers)
            layer.strength=layer.cmykChannel==0?1.12f:1f;
        baseline.widthMm=73; baseline.marginMm=2;baseline.registration=false;
        baseline.masterSpacingMm=2.85f;baseline.minimumMm=.72f;baseline.bridgeMm=.4f;
        baseline.detailBoost=.35f;baseline.shapeDefinition=.63f;
        int w=83,h=79;
        for(int fixture=0;fixture<2;fixture++){
            int[] src=photo(w,h,fixture,baseline.paper);
            for(int variant=0;variant<3;variant++){
                for(int style:STYLES){
                    VinylEngine.Settings s=baseline.copy();s.pattern=style;
                    if(variant==1){s.portraitMidtones=0;s.shapeDefinition=0;s.detailBoost=0;}
                    if(variant==2){s.portraitMidtones=.85f;s.shapeDefinition=.95f;s.detailBoost=.75f;}
                    VinylEngine.Result result=VinylEngine.generate(src,w,h,s);
                    for(int layer=0;layer<4;layer++){
                        String svg=VinylEngine.svg(result,layer);
                        System.out.println(fixture+" "+variant+" "+style+" "+layer+" "+result.shapes.get(layer).size()+" "+sha(svg));
                    }
                }
            }
        }
    }
}

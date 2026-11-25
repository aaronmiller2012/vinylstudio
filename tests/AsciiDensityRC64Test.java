package com.halftonestudio;
import java.util.*;
/** Regression for the exact high-detail phone spacing values in RC63 screenshots. */
public final class AsciiDensityRC64Test {
    static void yes(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    static int[] portrait(int w,int h){
        int[] p=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float nx=(x-w*.5f)/(w*.48f),ny=(y-h*.5f)/(h*.48f);
            float v=(float)Math.exp(-3*(nx*nx+ny*ny));
            int a=(int)(255*(.08+.8*v)),b=(int)(255*(.12+.70*nx*nx)),c=(int)(255*(.3+.5*ny*ny));
            p[y*w+x]=0xff000000|Math.min(255,a)<<16|Math.min(255,b)<<8|Math.min(255,c);
        }
        return p;
    }
    static void directSelection(){
        float[] spaces={.165f,.245f,.395f};
        for(float inches:spaces){
            float f=inches*25.4f*.70710678f*.88f;
            AsciiScreenMotif selector=new AsciiScreenMotif();
            HashSet<Character> unique=new HashSet<>();int compound=0;
            for(int i=0;i<250;i++){
                float coverage=.1f+.8f*(i%13)/12f;
                VinylEngine.Shape sh=selector.choose(15,15,f,coverage,.8f,
                        (i%7-3)*.1f,(i%5-2)*.1f,i/16,i%16);
                if(sh!=null){
                    yes(sh.bounds()[0]>=15-f/2-.001f&&sh.bounds()[2]<=15+f/2+.001f,"glyph exceeds screen footprint");
                    unique.add(selector.lastSymbol());
                    if(sh.contours!=null)compound++;
                    selector.accepted(i/16);
                }
            }
            System.out.println("screen "+inches+" in: symbols="+unique+" connected="+compound);
            yes(unique.size()>= (inches<.2f?8:inches<.3f?12:17),"too few symbols at "+inches);
            yes(compound >= (inches<.2f?20:90),"no connected glyphs at "+inches);
        }
    }
    static void sameCmykLayers(){
        int[] image=portrait(48,60);int prev=0;
        for(float spacing:new float[]{.395f,.245f,.165f}){
            VinylEngine.Settings s=VinylEngine.Settings.defaults();
            s.pattern=VinylEngine.ASCII;s.registration=false;s.minimumMm=.8f;s.widthMm=100f;s.marginMm=3f;
            s.masterSpacingMm=spacing*25.4f;s.shapeScale=.88f;
            long start=System.currentTimeMillis();
            VinylEngine.Result r=VinylEngine.generate(image,48,60,s);
            int n=r.count();System.out.println("screen "+spacing+" count="+n+" time="+(System.currentTimeMillis()-start)+"ms");
            yes(r.shapes.size()==4,"CMYK sheets changed");
            yes(n>0,"no shapes generated");
            yes(n>=prev*.75f,"increased resolution removed most motifs");
            yes(VinylEngine.validateExport(r)==null,"invalid export");
            prev=n;
        }
    }
    public static void main(String[] args){directSelection();sameCmykLayers();System.out.println("PASS RC64 size-specific vector variety, CMYK density and cut-safe exports");}
}

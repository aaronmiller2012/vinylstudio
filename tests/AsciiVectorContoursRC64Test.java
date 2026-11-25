package com.halftonestudio;
import java.util.*;
/** Verify normalized glyph templates and repeated cut geometry are closed, bounded and nondegenerate. */
public final class AsciiVectorContoursRC64Test {
    static void ok(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static double area(float[] p){double sum=0;for(int i=0;i<p.length;i+=2){int j=(i+2)%p.length;sum+=p[i]*p[j+1]-p[j]*p[i+1];}return sum*.5;}
    public static void main(String[] args){
        int tested=0,compound=0;
        for(AsciiVectorGlyphs.Glyph g:AsciiVectorGlyphs.all()){
            for(float ratio:new float[]{g.minScale,g.minScale+1f,g.minScale+3f}){
                VinylEngine.Shape sh=AsciiVectorGlyphs.shape(g,20,30,.8f*ratio,.8f);
                ok(sh!=null,"missing glyph "+g.letter);
                ok(sh.contours!=null,"glyph not closed vector "+g.letter);
                float[] bounds=sh.bounds();float s=.8f*ratio;
                ok(bounds[0]>=20-s/2-.001f&&bounds[2]<=20+s/2+.001f&&bounds[1]>=30-s/2-.001f&&bounds[3]<=30+s/2+.001f,"glyph outside screen "+g.letter);
                ok(sh.contours.length>=1&&sh.contours.length<=12,"too many disjoint cuts "+g.letter);
                for(float[] ring:sh.contours){
                    ok(ring.length>=6&&ring.length%2==0,"bad contour "+g.letter);
                    ok(Math.abs(area(ring))>.00001,"degenerate contour "+g.letter);
                    for(float coord:ring)ok(Float.isFinite(coord),"nonfinite glyph vertex "+g.letter);
                }
                tested++;compound+=sh.contours.length;
            }
        }
        System.out.println("PASS RC64 vector contour bounds, ring integrity and finite geometry "+tested+" templates, "+compound+" cut loops");
    }
}

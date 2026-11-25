package com.halftonestudio;

import java.util.*;

/** The new ASCII path shares color separation and screens with geometric Shapes. */
public final class AsciiShapesRC63Test {
    static void yes(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static int[] image(int w,int h){
        int[] px=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int red=(x*255)/(w-1),green=(y*255)/(h-1),blue=(x*31+y*17)&255;
            px[y*w+x]=0xff000000|(red<<16)|(green<<8)|blue;
        }return px;
    }
    static void testSizeSafety(){
        AsciiScreenMotif a=new AsciiScreenMotif();
        for(int i=0;i<64;i++){
            VinylEngine.Shape shape=a.choose(20,20,1.5f,.45f,.8f,.3f,.2f,i,2*i);
            yes(shape!=null,"no small safe punctuation");
            float[] b=shape.bounds();
            yes(b[0]>=19.25f-.001&&b[2]<=20.75f+.001&&b[1]>=19.25f-.001&&b[3]<=20.75f+.001,"motif exceeds footprint");
            yes(shape.contours==null,"pixel text illegally squeezed into small cell");
        }
        AsciiScreenMotif bigger=new AsciiScreenMotif();int complex=0;
        for(int i=0;i<120;i++){
            VinylEngine.Shape shape=bigger.choose(20,20,8f,.45f,.8f,.3f,.1f,i,i+3);
            yes(shape!=null,"large glyph missing");
            float[] b=shape.bounds();yes(b[0]>=16-.001&&b[2]<=24+.001&&b[1]>=16-.001&&b[3]<=24+.001,"large glyph outside footprint");
            if(shape.contours!=null)complex++;
        }
        yes(complex>10,"complex cuttable characters never selected at legal scale");
    }
    static void testSharedSeparation(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.pattern=VinylEngine.ASCII;s.minimumMm=.55f;s.marginMm=3f;s.masterSpacingMm=6.8f;
        s.registration=false;
        int[] px=image(56,52);
        VinylEngine.Result a=VinylEngine.generate(px,56,52,s);
        yes(a.shapes.size()==4,"CMYK channel count changed");
        for(int i=0;i<4;i++){
            yes(a.settings.layers.get(i).cmykChannel==new int[]{2,0,1,3}[i],"channel identity changed");
            yes(a.shapes.get(i).size()>0,"CMYK channel unexpectedly empty: "+i);
            String svg=VinylEngine.svg(a,i);
            yes(svg.contains("<path")&&!svg.contains("clipPath"),"real closed SVG glyph missing");
            yes(VinylEngine.svgElementsForLayer(a,i)<=5000,"SVG path budget exceeded");
        }
        // Selecting ASCII cannot change the channel separation, just the motif.
        s.pattern=VinylEngine.ROUND;
        VinylEngine.Result round=VinylEngine.generate(px,56,52,s);
        for(int i=0;i<4;i++){
            yes(a.settings.layers.get(i).angle==round.settings.layers.get(i).angle,"CMYK screen angle changed");
            yes(a.settings.layers.get(i).color==round.settings.layers.get(i).color,"CMYK swatch changed");
        }
        s.pattern=VinylEngine.ASCII;
        s.snips.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.40f,.40f,.62f,.40f,.62f,.63f,.40f,.63f));
        VinylEngine.Result masked=VinylEngine.generate(px,56,52,s);
        for(List<VinylEngine.Shape> layer:masked.shapes)for(VinylEngine.Shape shape:layer){
            float[][] rings=shape.contours==null?new float[][]{shape.xy}:shape.contours;
            for(float[] ring:rings)yes(s.snips.allowsPolygon(ring,s.widthMm,masked.heightMm),"Snip boundary crossed by glyph contour");
        }
        yes(VinylEngine.validateExport(masked)==null,"SVG preflight failed: "+VinylEngine.validateExport(masked));
    }
    static void testSpotAndIdentity(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();s.mode=VinylEngine.SPOT;
        s.pattern=VinylEngine.ASCII;s.minimumMm=.55f;s.registration=false;
        s.masterSpacingMm=7f;
        for(int count=2;count<=8;count++){
            while(s.layers.size()>count)s.layers.remove(s.layers.size()-1);
            while(s.layers.size()<count)s.layers.add(new VinylEngine.Layer("Accent",VinylEngine.START_COLORS[s.layers.size()]));
            VinylEngine.Result r=VinylEngine.generate(image(35,29),35,29,s);
            yes(r.shapes.size()==count,"spot-layer count changed at "+count);
            for(int i=0;i<count;i++)yes(VinylEngine.svg(r,i).contains("<svg"),"missing sheet");
        }
        yes(VinylEngine.ASCII==27&&VinylEngine.WORD_SCREEN==28&&VinylEngine.PATTERNS.length==29,"saved shape style IDs changed");
    }
    public static void main(String[] args){
        testSizeSafety();testSharedSeparation();testSpotAndIdentity();
        System.out.println("PASS RC63 size-aware ASCII Shapes, physical contours, CMYK/spot separation, Snip, SVG and style IDs");
    }
}

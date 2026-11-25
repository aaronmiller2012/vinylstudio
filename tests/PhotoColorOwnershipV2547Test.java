package com.halftonestudio;

import java.util.*;
/** Regression: green clothing and blue eyes must not paint a warm face green. */
public final class PhotoColorOwnershipV2547Test {
    private static void ok(boolean c,String why){if(!c)throw new AssertionError(why);}
    private static final int SKIN=0xffd59c82,GREEN=0xff4d9067,BLUE=0xff34497b;
    private static int[] palette(){return new int[]{0xff794638,0xffbc7f6a,SKIN,0xffecc8a8,GREEN,BLUE,0xffded6c0};}
    private static int[] picture(int w,int h,boolean clothing,boolean eye){
        int[] src=new int[w*h];Arrays.fill(src,SKIN);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            if(clothing&&y>=145)src[y*w+x]=GREEN;
            if(eye&&y>=62&&y<88&&x>=52&&x<85)src[y*w+x]=BLUE;
        }
        src[98*w+108]=GREEN; // isolated sensor/compression-color error on the face
        return src;
    }
    public static void main(String[] args){
        final int w=190,h=220;int[] pal=palette();
        var matcher=new MultiColorStippleEngine.PhotoColorMatcher(pal,7,null);
        ok(matcher.choose(SKIN)==2,"skin matched wrong swatch");
        ok(matcher.choose(GREEN)==4,"genuine green failed");
        ok(matcher.choose(BLUE)==5,"genuine blue iris failed");
        var to=new TessellationEngine.Options();to.colors=pal;to.layerCount=7;
        to.detail=33;to.style=TessellationEngine.GRID_MOTIF;to.registration=false;
        var tess=TessellationEngine.generate(picture(w,h,true,true),w,h,to);
        int face=0,wrongGreen=0,shirt=0,shirtGreen=0,eye=0,eyeBlue=0;
        for(var t:tess.tiles){
            float x=0,y=0;for(int i=0;i<t.xy.length;i+=2){x+=t.xy[i];y+=t.xy[i+1];}
            x/=t.xy.length/2;y/=t.xy.length/2;
            if(y>25&&y<138 && (x<48 || x>95)){face++;if(t.layer==4)wrongGreen++;}
            if(y>155){shirt++;if(t.layer==4)shirtGreen++;}
            if(y>64&&y<85&&x>55&&x<82){eye++;if(t.layer==5)eyeBlue++;}
        }
        ok(face>30 && wrongGreen==0,"green swatch contaminates warm face: "+wrongGreen+"/"+face);
        ok(shirt>20 && shirtGreen>=shirt*.8,"real green shirt lost: "+shirtGreen+"/"+shirt);
        ok(eye>0 && eyeBlue>0,"real blue iris lost");
        ok(tess.combinedSvg().contains("<svg")&&tess.combinedSvg().contains("<g "),"tessellation combined SVG invalid");
        // No forced swatch use when an image provides no evidence for it.
        var warm=TessellationEngine.generate(picture(w,h,false,false),w,h,to);
        ok(warm.counts[4]==0,"green vinyl manufactured to fill color quota");
        // Explicit swatches are authoritative, no invented or re-quantized colors.
        to.colors=new int[]{SKIN,GREEN};to.layerCount=2;
        var two=TessellationEngine.generate(picture(w,h,true,false),w,h,to);
        ok(two.counts[0]>0&&two.counts[1]>0,"selected-roll palette ignored");
        var so=new MultiColorStippleEngine.Options();so.layerCount=7;so.colors=pal;
        so.detail=32;so.widthMm=180;so.draft=true;
        var points=MultiColorStippleEngine.generate(picture(w,h,true,true),w,h,so);
        int faceDots=0,greenDots=0,shirtDots=0,shirtGreenDots=0;
        for(var d:points.dots){
            if(d.y>20&&d.y<135&&(d.x<45||d.x>95)){faceDots++;if(d.layer==4)greenDots++;}
            if(d.y>160){shirtDots++;if(d.layer==4)shirtGreenDots++;}
        }
        ok(faceDots>15&&greenDots==0,"stipple green contamination: "+greenDots+"/"+faceDots);
        ok(shirtDots>15&&shirtGreenDots>=shirtDots*.75,"stipple legitimate green lost");
        System.out.println("PASS v2.54.7 source-color matching, accent coherence, swatch preservation, tessellation SVG and stipple ownership; face="+face+" shirt="+shirt+" eye="+eye+" dots="+points.dots.size());
    }
}

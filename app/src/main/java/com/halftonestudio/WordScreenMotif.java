package com.halftonestudio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

/**
 * RC65 whole-word Shapes motif. Uses EXACTLY the standard CMYK/spot channel
 * coverage, layer list, Snip crop, physical units and SVG pipeline. This is
 * not the older TEXT portrait engine and does not force an opaque underlay.
 *
 * Candidate placements are prioritized by the selected channel's photographic
 * coverage and local contrast, then packed with a conservative same-layer
 * footprint occupancy grid. Each word is constructed from complete, size-safe
 * closed vector glyphs; there are no SVG fonts, clipPaths or partial words.
 */
public final class WordScreenMotif {
    private WordScreenMotif() {}
    private static final int MAX_CANDIDATES=7200;
    private static final int MAX_LOOPS=4750;
    private static final float[] SCALES={1f,1.18f,1.42f};

    /** Normalization is shared by Save/Open, Undo/Redo and the edit dialog. */
    public static String normalize(String text) {
        if(text==null)return "";
        String[] parts=text.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+");
        StringBuilder b=new StringBuilder();
        for(String part:parts){
            if(part.length()==0)continue;
            if(part.length()>16)part=part.substring(0,16);
            if(b.length()+part.length()+1>240)break;
            if(b.length()>0)b.append(' ');
            b.append(part);
        }
        return b.toString();
    }
    private static final class Token {
        final String value;
        final AsciiVectorGlyphs.Glyph[] glyphs;
        final float minHeight;
        Token(String value,float minimum){
            this.value=value;glyphs=new AsciiVectorGlyphs.Glyph[value.length()];
            float required=minimum;
            for(int i=0;i<value.length();i++){
                AsciiVectorGlyphs.Glyph g=AsciiVectorGlyphs.find(value.charAt(i));
                if(g==null)throw new IllegalArgumentException("Missing glyph: "+value.charAt(i));
                glyphs[i]=g;required=Math.max(required,g.minScale*minimum);
            }
            minHeight=required;
        }
    }
    private static final class Candidate {
        final float left,top,height,width,score;
        final int token;
        Candidate(float x,float y,float h,float w,float score,int token){
            left=x;top=y;height=h;width=w;this.score=score;this.token=token;
        }
    }
    private static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
    private static float pitch(float h,float min){return h*.84f+min*.20f;}
    private static float width(Token word,float h,float min){
        return word.value.length()*pitch(h,min)-min*.20f;
    }
    private static int sample(int[] image,int w,int h,float x,float y,float widthMm,float heightMm){
        int xx=Math.max(0,Math.min(w-1,(int)(x/widthMm*w)));
        int yy=Math.max(0,Math.min(h-1,(int)(y/heightMm*h)));
        return image[yy*w+xx];
    }
    /** Candidate score is computed strictly from that layer's existing coverage.
     * No new CMYK conversion and no attempt at transparent ink simulation.
     */
    private static float score(int[] pixels,int w,int h,VinylEngine.Result result,int layer,
            float x,float y,float ww,float hh,int index){
        VinylEngine.Settings s=result.settings;
        float x0=x+ww*.15f,x1=x+ww*.5f,x2=x+ww*.85f,cy=y+hh*.5f;
        float a=VinylEngine.coverage(sample(pixels,w,h,x0,cy,s.widthMm,result.heightMm),s,layer);
        float b=VinylEngine.coverage(sample(pixels,w,h,x1,cy,s.widthMm,result.heightMm),s,layer);
        float c=VinylEngine.coverage(sample(pixels,w,h,x2,cy,s.widthMm,result.heightMm),s,layer);
        float t=VinylEngine.coverage(sample(pixels,w,h,x1,y+hh*.25f,s.widthMm,result.heightMm),s,layer);
        float d=VinylEngine.coverage(sample(pixels,w,h,x1,y+hh*.75f,s.widthMm,result.heightMm),s,layer);
        float coverage=(a+b+c+t+d)*.2f;
        float structure=(Math.abs(a-b)+Math.abs(b-c)+Math.abs(t-d))*.333f;
        if(coverage<.075f)return -1;
        // Dense/structural patches should be served first. A small deterministic
        // tie-break prevents harsh repeated horizontal stripes.
        int hash=(int)(x*131+y*191+layer*977+index*137);
        hash^=hash>>>13;hash*=0x9e3779b9;hash^=hash>>>16;
        float tie=(hash&255)/255f;
        return coverage*.82f+Math.min(.18f,structure*.28f)+tie*.075f;
    }
    /** A rectangle exclusion protects the physical word envelope, not only
     * glyph centers, from intra-layer word collisions and short sliver gaps.
     */
    private static final class Occupancy {
        final int width,height;
        final float grid;
        final boolean[] cells;
        Occupancy(float w,float h,float min){
            grid=Math.max(.55f,min*.65f);
            width=(int)Math.ceil(w/grid)+1;height=(int)Math.ceil(h/grid)+1;
            cells=new boolean[width*height];
        }
        private int[] region(float x,float y,float w,float h,float gap){
            int x0=Math.max(0,(int)Math.floor((x-gap)/grid));
            int y0=Math.max(0,(int)Math.floor((y-gap)/grid));
            int x1=Math.min(width-1,(int)Math.ceil((x+w+gap)/grid));
            int y1=Math.min(height-1,(int)Math.ceil((y+h+gap)/grid));
            return new int[]{x0,y0,x1,y1};
        }
        boolean available(float x,float y,float w,float h,float gap){
            int[] a=region(x,y,w,h,gap);
            for(int yy=a[1];yy<=a[3];yy++)for(int xx=a[0];xx<=a[2];xx++)if(cells[yy*width+xx])return false;
            return true;
        }
        void occupy(float x,float y,float w,float h,float gap){
            int[] a=region(x,y,w,h,gap);
            for(int yy=a[1];yy<=a[3];yy++)for(int xx=a[0];xx<=a[2];xx++)cells[yy*width+xx]=true;
        }
    }
    private static VinylEngine.Shape wordShape(Token word,float x,float y,float size,float minimum){
        ArrayList<float[]> paths=new ArrayList<>();
        for(int i=0;i<word.glyphs.length;i++){
            AsciiVectorGlyphs.Glyph glyph=word.glyphs[i];
            float cx=x+pitch(size,minimum)*i+size*.5f;
            VinylEngine.Shape part=AsciiVectorGlyphs.shape(glyph,cx,y+size*.5f,size,minimum);
            if(part==null)return null;
            if(part.contours!=null){
                for(float[] ring:part.contours)paths.add(ring);
            }else if(part.xy!=null)paths.add(part.xy);
            if(paths.size()>MAX_LOOPS)return null;
        }
        if(paths.isEmpty())return null;
        return VinylEngine.Shape.compound(paths.toArray(new float[paths.size()][]));
    }
    public static void generate(VinylEngine.Result result,int[] pixels,int imageW,int imageH,int layer){
        VinylEngine.Settings s=result.settings;
        float min=Math.max(.20f,s.minimumMm);
        String normalized=normalize(s.screenWords);
        if(normalized.isEmpty())normalized="VINYL STUDIO";
        String[] pieces=normalized.split(" ");
        Token[] words=new Token[Math.min(24,pieces.length)];
        for(int i=0;i<words.length;i++)words[i]=new Token(pieces[i],min);
        float smallest=Float.POSITIVE_INFINITY;
        for(Token word:words)smallest=Math.min(smallest,word.minHeight);
        // Sampling resolution is bounded: more detailed Spacing offers more
        // candidate placements but never shrinks a letter below its own limit.
        float sp=Math.max(.8f,s.layers.get(layer).spacing);
        float stride=Math.max(min*1.1f,Math.min(sp*.76f,smallest*.94f));
        // Scan the WHOLE canvas: never stop after filling the first N rows.
        // Adapt the grid resolution to a fixed work budget on large images.
        float usableW=Math.max(1,s.widthMm-2*(s.marginMm+min*.25f));
        float usableH=Math.max(1,result.heightMm-2*(s.marginMm+min*.25f));
        stride=Math.max(stride,(float)Math.sqrt(usableW*usableH*3.3f/MAX_CANDIDATES));
        ArrayList<Candidate> candidates=new ArrayList<>();
        float margin=s.marginMm+min*.25f;
        int probes=0;
        for(float y=margin;y+smallest<result.heightMm-margin;y+=stride){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            int row=(int)(y/stride);
            float stagger=(row%3)*stride/3f+(layer%2)*stride*.15f;
            for(float x=margin+stagger;x<s.widthMm-margin-smallest;x+=stride){
                if(++probes%128==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                int tokenIndex=(row*17+(int)(x/stride)*7+layer*3)%words.length;
                for(int variant=0;variant<3;variant++){
                    Token word=words[(tokenIndex+variant)%words.length];
                    float base=Math.max(word.minHeight,Math.min(sp*1.2f*s.shapeScale,smallest*1.35f));
                    float size=Math.max(word.minHeight,base*SCALES[variant]);
                    float ww=width(word,size,min);
                    if(x+ww>=s.widthMm-margin||y+size>=result.heightMm-margin)continue;
                    float quality=score(pixels,imageW,imageH,result,layer,x,y,ww,size,variant);
                    if(quality<0)continue;
                    // Prefer a range of physical text sizes instead of one
                    // mechanical row size. Long words can still win in dark areas.
                    quality+=Math.min(.055f,variant*.017f);
                    if(candidates.size()<MAX_CANDIDATES)
                        candidates.add(new Candidate(x,y,size,ww,quality,(tokenIndex+variant)%words.length));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble((Candidate c)->c.score).reversed());
        Occupancy occupancy=new Occupancy(s.widthMm,result.heightMm,min);
        int contourCount=VinylEngine.svgElementsForLayer(result,layer),accepted=0;
        for(Candidate c:candidates){
            if((accepted&63)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            if(!occupancy.available(c.left,c.top,c.width,c.height,min*.42f))continue;
            VinylEngine.Shape word=wordShape(words[c.token],c.left,c.top,c.height,min);
            if(word==null)continue;
            int contour=word.contours==null?1:word.contours.length;
            if(contourCount+contour>=MAX_LOOPS)break;
            int before=result.shapes.get(layer).size();
            VinylEngine.add(result,layer,word); // Includes circle crop, Snip holes and margins.
            if(result.shapes.get(layer).size()==before)continue;
            contourCount+=contour;accepted++;
            occupancy.occupy(c.left,c.top,c.width,c.height,min*.42f);
            if(accepted>=750)break;
        }
    }
}

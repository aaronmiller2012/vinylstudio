package com.halftonestudio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

/**
 * RC66: multilayer typographic portrait layout.
 *
 * The first physical layer is the solid Snip silhouette (generated upstream).
 * Remaining layers contain intact cuttable words, with one paint/vinyl color
 * per word. A bounded multi-scale candidate search prioritizes photo edges,
 * actual palette fidelity, and broad-image coverage rather than scanning fixed
 * horizontal lines. A shared occupancy mask prevents overprinting words across
 * opaque vinyl layers. The engine never changes the user's palette or Snip.
 *
 * Inspired by irregular typographic packing and multi-centric shape packing;
 * this is a constrained deterministic approximation, not a font cloud widget.
 */
public final class WordPortraitEngine {
    private WordPortraitEngine() {}
    private static final int CANDIDATE_LIMIT=6600;
    private static final int WORD_LIMIT=700;
    private static final int CONTOUR_LIMIT=4600; // Keep SVG path budget below 5000.
    private static final float[] SCALE={1f,1.32f,1.72f};

    public static String[] tokenize(String input){
        if(input==null)return new String[0];
        String[] parts=input.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+");
        ArrayList<String> valid=new ArrayList<>();
        for(String p:parts)if(!p.isEmpty()){
            if(p.length()>16)p=p.substring(0,16);
            if(valid.size()<32)valid.add(p);
        }
        return valid.toArray(new String[0]);
    }
    private static final class Token {
        final String text;final WordVectorGlyphs.Glyph[] glyphs;final float minHeight;
        Token(String text,float minimum){
            this.text=text;glyphs=new WordVectorGlyphs.Glyph[text.length()];
            float required=minimum;
            for(int i=0;i<text.length();i++){if(((i)&63)==0)CancellationCheckpoint.check();
                WordVectorGlyphs.Glyph glyph=WordVectorGlyphs.find(text.charAt(i));
                if(glyph==null)throw new IllegalArgumentException("Missing vector glyph for "+text.charAt(i));
                glyphs[i]=glyph;required=Math.max(required,glyph.minScale*minimum);
            }
            minHeight=required;
        }
    }
    private static final class Candidate {
        final float x,y,h,w,score;final int token,layer;
        Candidate(float x,float y,float h,float w,float score,int token,int layer){
            this.x=x;this.y=y;this.h=h;this.w=w;this.score=score;this.token=token;this.layer=layer;
        }
    }
    /** Occupied across ALL detail colors to keep the complete words readable
     * when opaque layers are stacked. Cell allocation is bounded by artwork mm. */
    private static final class Occupancy {
        final float grid;final int w,h;final boolean[] taken;
        Occupancy(float widthMm,float heightMm,float min){
            grid=Math.max(.70f,min*.90f);
            w=(int)Math.ceil(widthMm/grid)+2;h=(int)Math.ceil(heightMm/grid)+2;
            taken=new boolean[w*h];
        }
        private int ix(float x){return Math.max(0,Math.min(w-1,(int)Math.floor(x/grid)));}
        private int iy(float y){return Math.max(0,Math.min(h-1,(int)Math.floor(y/grid)));}
        boolean clear(float x,float y,float width,float height,float gap){
            for(int yy=iy(y-gap);yy<=iy(y+height+gap);yy++)
                for(int xx=ix(x-gap);xx<=ix(x+width+gap);xx++)
                    if(taken[yy*w+xx])return false;
            return true;
        }
        void add(float x,float y,float width,float height,float gap){
            for(int yy=iy(y-gap);yy<=iy(y+height+gap);yy++)
                for(int xx=ix(x-gap);xx<=ix(x+width+gap);xx++)taken[yy*w+xx]=true;
        }
    }
    private static float advance(float height,float minimum){return height*.83f+minimum*.18f;}
    private static float wordWidth(Token token,float height,float minimum){
        return token.text.length()*advance(height,minimum)-minimum*.18f;
    }
    private static float clamp(float a,float lo,float hi){return Math.max(lo,Math.min(hi,a));}
    /** Measure the hypothetical opaque-vinyl color improvement over underlay.
     * The SLIC-inspired region is a soft guide; actual local photo RGB wins.
     * Do not force every layer to have equal word counts. */
    private static Candidate evaluate(StyleKit.Source src,TextPortraitRegions.Map regions,
            Token token,int tokenIndex,float x,float y,float h,float min,float[][] lab,
            int seed){
        float width=wordWidth(token,h,min);
        if(x<min*.5f||y<min*.5f||x+width>=src.widthMm-min*.5f||y+h>=src.heightMm-min*.5f)return null;
        float[] gain=new float[lab.length];float edges=0;int tested=0;
        for(int j=0;j<token.text.length();j++){if(((j)&63)==0)CancellationCheckpoint.check();
            float xx=x+j*advance(h,min)+h*.41f,yy=y+h*.52f;
            if(!src.validAt(xx,yy))return null;
            TextPortraitRegions.Region reg=regions.at(xx,yy);
            if(reg==null)return null;
            float[] original=StyleKit.toLab(src.colorAt(xx,yy));
            float[] guide=StyleKit.toLab(reg.rgb);
            float[] target={original[0]*.82f+guide[0]*.18f,
                original[1]*.82f+guide[1]*.18f,original[2]*.82f+guide[2]*.18f};
            float base=StyleKit.labDist2(target,lab[0]);
            for(int k=1;k<lab.length;k++)gain[k]+=(base-StyleKit.labDist2(target,lab[k]))/3000f;
            edges+=reg.edge;tested++;
        }
        int chosen=-1;float best=-10000;
        for(int k=1;k<lab.length;k++){if(((k)&63)==0)CancellationCheckpoint.check();
            // Never select a color that cannot be distinguished from the base.
            float contrast=StyleKit.labDist2(lab[0],lab[k]);
            // Retain a low-contrast but user-selected swatch for two-layer
            // projects. Never silently replace user-selected vinyl colors.
            if(contrast<(lab.length==2?4f:14f))continue;
            float value=gain[k]/tested+.12f*(edges/tested)
                +Math.min(.09f,(float)Math.sqrt(contrast)*.002f);
            if(value>best){best=value;chosen=k;}
        }
        if(chosen<1||best<-.22f)return null;
        // Encourage a meaningful range of heights while preventing text strips.
        float hash=(float)((((seed*1103515245L+12345L)>>>12)&255)/255.0);
        return new Candidate(x,y,h,width,best+.035f*h/Math.max(2.5f,token.minHeight)+hash*.025f,
            tokenIndex,chosen);
    }
    /** True cut contours, not SVG <text> or strokes. Sampling vertices, edge
     * intervals, and glyph interior points prevents crossing narrow Snip holes. */
    private static ArrayList<float[]> geometry(StyleKit.Source src,Token token,
            float left,float top,float height,float min){
        ArrayList<float[]> loops=new ArrayList<>();
        float pitch=advance(height,min);
        for(int j=0;j<token.glyphs.length;j++){if(((j)&63)==0)CancellationCheckpoint.check();
            float centerX=left+j*pitch+height*.5f,centerY=top+height*.5f;
            if(!src.validAt(centerX,centerY))return null;
            ArrayList<float[]> contours=WordVectorGlyphs.contours(token.glyphs[j],centerX,centerY,height,min);
            if(contours==null)return null;
            for(float[] path:contours){CancellationCheckpoint.check();
                if(path==null||path.length<6)return null;
                for(int i=0;i<path.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();
                    float ax=path[i],ay=path[i+1],bx=path[(i+2)%path.length],by=path[(i+3)%path.length];
                    float dist=(float)Math.hypot(ax-bx,ay-by);
                    int steps=Math.max(1,(int)Math.ceil(dist/Math.max(.4f,min*.52f)));
                    for(int t=0;t<=steps;t++){if(((t)&63)==0)CancellationCheckpoint.check();
                        float f=t/(float)steps;
                        if(!src.validAt(ax+(bx-ax)*f,ay+(by-ay)*f))return null;
                    }
                }
                loops.add(path);
                if(loops.size()>150)return null;
            }
        }
        return loops;
    }
    static String render(StyleKit.Source src,TextHalftoneEngine.Options options,
                  TextPortraitRegions.Map regions,StyleKit.Layer[] layers,float cell){
        String[] list=tokenize(options.text);
        if(list.length==0)list=new String[]{"VINYL","STUDIO"};
        float minimum=Math.max(.3f,options.minStrokeMm);
        Token[] words=new Token[list.length];float smallest=Float.MAX_VALUE;
        for(int i=0;i<words.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            words[i]=new Token(list[i],minimum);
            smallest=Math.min(smallest,words[i].minHeight);
        }
        int[] colors=new int[layers.length];for(int i=0;i<colors.length;i++)colors[i]=layers[i].color;
        float[][] lab=StyleKit.labOf(colors);
        float area=src.widthMm*src.heightMm;
        float stride=Math.max(minimum*1.2f,Math.min(cell*.70f,smallest*.77f));
        // Keep candidate work bounded for a large photo and 8 vinyl layers.
        stride=Math.max(stride,(float)Math.sqrt(area*3.2f/CANDIDATE_LIMIT));
        ArrayList<Candidate> proposals=new ArrayList<>();int probes=0;
        for(float y=minimum*.6f;y+smallest<src.heightMm-minimum*.6f;y+=stride){
            StyleKit.checkCancelled();
            int row=(int)(y/stride);
            float stagger=(row%3)*stride/3f;
            for(float x=minimum*.6f+stagger;x+smallest<src.widthMm-minimum*.6f;x+=stride){CancellationCheckpoint.check();
                if((++probes&127)==0)StyleKit.checkCancelled();
                for(int variant=0;variant<SCALE.length;variant++){if(((variant)&63)==0)CancellationCheckpoint.check();
                    int index=(row*7+(int)(x/stride)*11+variant*3)%words.length;
                    Token token=words[index];
                    float height=Math.max(token.minHeight,
                        Math.max(smallest,Math.min(cell*1.02f,smallest*1.14f))*SCALE[variant]);
                    Candidate candidate=evaluate(src,regions,token,index,x,y,height,minimum,lab,
                        row*101+(int)(x/stride)*13+variant*37);
                    if(candidate!=null&&proposals.size()<CANDIDATE_LIMIT)proposals.add(candidate);
                }
            }
        }
        proposals.sort(Comparator.comparingDouble((Candidate c)->c.score).reversed());
        Occupancy occupancy=new Occupancy(src.widthMm,src.heightMm,minimum);
        int[] usage=new int[layers.length];int total=0,paths=0,rejected=0;
        for(Candidate c:proposals){CancellationCheckpoint.check();
            if((total&31)==0)StyleKit.checkCancelled();
            if(total>=WORD_LIMIT||paths>=CONTOUR_LIMIT)break;
            if(!occupancy.clear(c.x,c.y,c.w,c.h,minimum*.38f))continue;
            ArrayList<float[]> geom=geometry(src,words[c.token],c.x,c.y,c.h,minimum);
            if(geom==null){rejected++;continue;}
            if(paths+geom.size()>CONTOUR_LIMIT)continue;
            layers[c.layer].loops.addAll(geom);
            usage[c.layer]++;total++;paths+=geom.size();
            occupancy.add(c.x,c.y,c.w,c.h,minimum*.38f);
        }
        StringBuilder summary=new StringBuilder("Words multilayer region-aware (solid Snip L1): ")
            .append(total).append(" whole words, ").append(paths)
            .append(" cut contours, ").append(regions.count())
            .append(" color regions, cell ")
            .append(String.format(Locale.US,"%.2f",cell)).append(" mm, ");
        for(int i=1;i<usage.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if(i>1)summary.append(", ");summary.append('L').append(i+1).append('=').append(usage[i]);
        }
        if(total==0)summary.append("; no safe complete words fit; shorten text or enlarge artwork");
        if(rejected>0)summary.append(" (").append(rejected).append(" rejected near Snip)");
        return summary.toString();
    }
}

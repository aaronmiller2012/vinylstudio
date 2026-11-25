package com.halftonestudio;

import java.util.*;

/**
 * Cuttable typographic portraits. A physically bounded character grid is sampled in
 * 5x7 subcells. ASCII characters are selected by the source's local dark/light SHAPE,
 * not by nine-pixel average brightness alone. WORDS preserve their sequence but
 * distribute glyphs with ordered tone dithering and never shrink below cut width.
 *
 * The default PixelFont5x7 supplies real CLOSED polygon contours (no live SVG text).
 * Snip masks, fixed vinyl palettes, original physical dimensions and cut-layer
 * assignments follow the StyleKit contract.
 */
public final class TextHalftoneEngine {
    private TextHalftoneEngine() {}

    public interface GlyphSource {
        ArrayList<float[]> outline(int codePoint);
        float aspect(int codePoint);
    }
    public enum Mode { RAMP, WORDS }
    public enum WordStyle { MOSAIC, KNOCKOUT }

    public static final class Options {
        public float widthMm = 152.4f;
        public float cellMm = 4.2f;
        public Mode mode = Mode.RAMP;
        public WordStyle wordStyle = WordStyle.MOSAIC;
        public String ramp = " .,:-=/\\|+*!%#@&MWOX\u2588";
        public String text = "VINYL STUDIO ";
        public boolean staggerRows = false;
        public boolean scaleByTone = false;
        public float minScale = .45f, maxScale = 1f;
        public float emptyBelow = .045f;
        public float minStrokeMm = .45f;
        public int colorLayers = 0;
        public int[] palette = null;
        public int inkArgb = 0xff101216, paperArgb = 0xfff7f4ec;
        public boolean autoLevels = true;
        public float gamma = 1f, contrast = 1f;
        public boolean edgeAware = true, orderedDither = true;
        /** RC60: physical first vinyl layer is a filled shape of the allowed Snip silhouette. */
        public boolean silhouetteUnderlay = false;
        public long seed = 2;
        public boolean[] allowed = null;
        public GlyphSource font = new PixelFont5x7();
        public Options copy() {
            Options o = new Options();
            o.widthMm=widthMm;o.cellMm=cellMm;o.mode=mode;o.wordStyle=wordStyle;o.ramp=ramp;o.text=text;
            o.staggerRows=staggerRows;o.scaleByTone=scaleByTone;o.minScale=minScale;
            o.maxScale=maxScale;o.emptyBelow=emptyBelow;o.minStrokeMm=minStrokeMm;
            o.colorLayers=colorLayers;o.palette=palette==null?null:palette.clone();
            o.inkArgb=inkArgb;o.paperArgb=paperArgb;o.autoLevels=autoLevels;
            o.gamma=gamma;o.contrast=contrast;o.edgeAware=edgeAware;
            o.orderedDither=orderedDither;o.silhouetteUnderlay=silhouetteUnderlay;o.seed=seed;
            o.allowed=allowed==null?null:allowed.clone();o.font=font;
            return o;
        }
        void validate() {
            if (!Float.isFinite(cellMm)||cellMm<2.5f||cellMm>30f) throw new IllegalArgumentException("cellMm must be 2.5..30");
            if (mode==null) throw new IllegalArgumentException("Missing text mode");
            if (wordStyle==null) throw new IllegalArgumentException("Missing word style");
            if (mode==Mode.RAMP&&(ramp==null||ramp.codePointCount(0,ramp.length())<2)) throw new IllegalArgumentException("ramp needs 2+ characters");
            if (mode==Mode.WORDS&&(text==null||text.trim().isEmpty())) throw new IllegalArgumentException("text is empty");
            if (minScale<=0||maxScale<minScale||maxScale>1.4f) throw new IllegalArgumentException("Invalid scale range");
            if (!Float.isFinite(minStrokeMm)||minStrokeMm<=0) throw new IllegalArgumentException("Invalid cut width");
            if (colorLayers!=0&&(colorLayers<2||colorLayers>8)) throw new IllegalArgumentException("colorLayers must be 0 or 2..8");
            if (palette!=null&&(palette.length<1||palette.length>8)) throw new IllegalArgumentException("Invalid palette");
            if (font==null) throw new IllegalArgumentException("font is null");
        }
    }

    static final class Tile {
        final float cx,cy,dark,edge;
        final float[] patch;
        final int color,row,col;
        final int[] rgb35;
        TextColorSeparation.Analysis evaluated;
        Tile(float cx,float cy,float dark,float edge,float[] patch,int[] rgb35,int color,int row,int col) {
            this.cx=cx;this.cy=cy;this.dark=dark;this.edge=edge;
            this.patch=patch;this.rgb35=rgb35;this.color=color;this.row=row;this.col=col;
        }
    }
    private static final int[] BAYER_4={0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5};

    /** Match the glyph's actual cut-path pixel mask against all 35 image subcells. */
    static int glyphForPatch(String ramp,float[] patch,float mean) {
        int selected=' ';
        float best=Float.POSITIVE_INFINITY;
        for(int off=0;off<ramp.length();) {if(((off)&63)==0)CancellationCheckpoint.check();
            int cp=ramp.codePointAt(off);off+=Character.charCount(cp);
            long bits=PixelFont5x7.mask(cp);
            if (bits==0 && cp!=' ') continue;
            int filled=Long.bitCount(bits);
            float mass=filled/35f;
            // Tone mass is independent of local edge shape: without this separation,
            // flat pale skin picks SPACE even when it differs visibly from paper.
            float cost=4f*(mass-mean)*(mass-mean);
            for(int b=0;b<35;b++) {if(((b)&63)==0)CancellationCheckpoint.check();
                float ink=((bits>>>b)&1L)!=0?1f:0f;
                float difference=(patch[b]-mean)-.65f*(ink-mass);
                cost+=difference*difference/35f;
            }
            cost+=filled*.000002f;
            if(cost<best){best=cost;selected=cp;}
        }
        return selected;
    }
    private static float clamp(float a){return Math.max(0f,Math.min(1f,a));}
    private static boolean shouldPlaceWord(Tile t,boolean dither,float cutoff) {
        if(t.dark<cutoff) return false;
        float coverage=clamp((t.dark-.035f)/.69f);
        coverage=coverage*coverage*(3-2*coverage); // smooth tonal falloff
        coverage=clamp(coverage+Math.min(.18f,t.edge*.32f));
        if(!dither) return coverage>=.4f;
        float threshold=(BAYER_4[(t.row&3)*4+(t.col&3)]+.5f)/16f;
        return coverage>=threshold;
    }
    public static StyleKit.Result generate(int[] argb,int w,int h,Options o) {
        o.validate();
        StyleKit.Source src=new StyleKit.Source(argb,w,h,o.allowed,o.widthMm);
        if(o.autoLevels)src.autoLevels(.02f,.98f);
        src.tone(o.gamma,o.contrast);
        if(!src.anyValid())throw new IllegalArgumentException("No usable pixels");
        float W=src.widthMm,H=src.heightMm;
        // 5x7 pixel-font bars are 1/7 of the character's height, at 92% cell fill.
        // Never try to produce detail by silently violating the vinyl minimum.
        float c=Math.max(o.cellMm,Math.max(o.minStrokeMm*7f/.92f*1.015f,
            o.minStrokeMm*8f*1.015f));
        int nx=Math.max(1,(int)(W/c)-(o.staggerRows?1:0)),ny=Math.max(1,(int)(H/c));
        float ox=(W-nx*c-(o.staggerRows?c/2f:0))/2f,oy=(H-ny*c)/2f;
        ArrayList<Tile> tiles=new ArrayList<>(nx*ny);
        for(int j=0;j<ny;j++){if(((j)&63)==0)CancellationCheckpoint.check();
            if((j&7)==0)StyleKit.checkCancelled();
            for(int i=0;i<nx;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                float cx=ox+(i+.5f)*c+(o.staggerRows&&(j&1)==1?c/2f:0f),cy=oy+(j+.5f)*c;
                if(!src.validAt(cx,cy))continue;
                float[] patch=new float[35];int[] rgb35=new int[35];float sum=0,lo=1,hi=0;
                int valid=0,r=0,g=0,b=0,colorN=0;
                for(int yy=0;yy<7;yy++)for(int xx=0;xx<5;xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
                    float sx=cx+((xx+.5f)/5f-.5f)*c*.76f;
                    float sy=cy+((yy+.5f)/7f-.5f)*c*.90f;
                    if(!src.validAt(sx,sy))continue;
                    valid++;
                    float d=clamp(src.darkAt(sx,sy));
                    patch[yy*5+xx]=d;sum+=d;lo=Math.min(lo,d);hi=Math.max(hi,d);
                    int col=src.colorAt(sx,sy);rgb35[yy*5+xx]=col;
                    r+=(col>>16)&255;g+=(col>>8)&255;b+=col&255;colorN++;
                }
                // Do not allow a glyph to cross snipped/transparent image boundaries.
                if(valid!=35)continue;
                float mean=sum/35f;
                if(mean<o.emptyBelow && hi-lo<.12f)continue;
                float edge=hi-lo;
                if(o.edgeAware){
                    // Enhance within-cell edges without shifting the cell's average tone.
                    for(int k=0;k<35;k++)patch[k]=clamp(mean+(patch[k]-mean)*1.6f);
                }
                int color=0xff000000|((r/colorN)<<16)|((g/colorN)<<8)|(b/colorN);
                tiles.add(new Tile(cx,cy,mean,edge,patch,rgb35,color,j,i));
            }
        }
        StyleKit.Result result=new StyleKit.Result(W,H);result.paperArgb=o.paperArgb;
        int[] palette=null;float[][] plab=null;
        if(o.palette!=null)palette=o.palette.clone();
        else if(o.colorLayers>=2&&!tiles.isEmpty()){
            int[] ca=new int[tiles.size()];float[] wa=new float[ca.length];
            for(int i=0;i<ca.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();ca[i]=tiles.get(i).color;wa[i]=1f;}
            palette=StyleKit.kmeans(ca,wa,o.colorLayers,o.seed);
            if(o.silhouetteUnderlay){
                // Generated palettes are arranged in physical order: light underlay,
                // increasingly dark top-detail layers. User-set palettes keep their order.
                Integer[] ordered=new Integer[palette.length];
                for(int i=0;i<ordered.length;i++)ordered[i]=palette[i];
                java.util.Arrays.sort(ordered,(a,b)->Integer.compare(StyleKit.luma255(b),StyleKit.luma255(a)));
                for(int i=0;i<palette.length;i++)palette[i]=ordered[i];
            }
        }
        StyleKit.Layer[] layers;
        if(palette!=null){
            plab=StyleKit.labOf(palette);layers=new StyleKit.Layer[palette.length];
            for(int i=0;i<layers.length;i++)layers[i]=new StyleKit.Layer(
                o.silhouetteUnderlay&&i==0?"snipped-background-1":"text-"+(i+1),palette[i]);
        } else if(o.silhouetteUnderlay) layers=new StyleKit.Layer[]{
            new StyleKit.Layer("snipped-background-1",o.paperArgb),
            new StyleKit.Layer("text-2",o.inkArgb)};
        else layers=new StyleKit.Layer[]{new StyleKit.Layer("text",o.inkArgb)};
        if(o.silhouetteUnderlay){
            if(layers.length<2)throw new IllegalArgumentException("Snipped background needs at least two colors");
            // The mask already includes Snip keep/remove operations, transparent areas,
            // circle crop, and output margins. Trace once into closed even-odd loops:
            // an actual solid vinyl silhouette, never an SVG clipPath or an outline stroke.
            layers[0].loops.addAll(VinylStyleSuite.subjectLoops(src));
            if(layers[0].loops.isEmpty())throw new IllegalArgumentException("Snip leaves no cuttable background");
        }
        if(o.silhouetteUnderlay){
            renderColorComposite(src,o,tiles,layers,c,nx,ny,result);
            return result;
        }
        int drawn=0,skipped=0,wordPos=0;
        int[] cps=o.mode==Mode.WORDS?o.text.codePoints().toArray():null;
        boolean needsMaskCheck=o.allowed!=null;
        if(!needsMaskCheck)for(boolean valid:src.valid)if(!valid){needsMaskCheck=true;break;}
        for(int k=0;k<tiles.size();k++){if(((k)&63)==0)CancellationCheckpoint.check();
            if((k&127)==0)StyleKit.checkCancelled();
            Tile t=tiles.get(k);
            int cp;
            float scale=1f;
            if(o.mode==Mode.RAMP){
                cp=o.edgeAware?glyphForPatch(o.ramp,t.patch,t.dark):
                    o.ramp.codePointAt(o.ramp.offsetByCodePoints(0,Math.min(
                        o.ramp.codePointCount(0,o.ramp.length())-1,
                        Math.round(t.dark*(o.ramp.codePointCount(0,o.ramp.length())-1)))));
                if(o.scaleByTone){
                    float floor=Math.max(o.minScale,o.minStrokeMm*7f/(c*.92f));
                    scale=Math.min(o.maxScale,Math.max(floor,floor+(o.maxScale-floor)*t.dark));
                }
            }else{
                if(!shouldPlaceWord(t,o.orderedDither,o.emptyBelow))continue;
                cp=cps[wordPos++%cps.length];
                float floor=Math.max(o.minScale,o.minStrokeMm*7f/(c*.92f));
                scale=Math.min(o.maxScale,Math.max(floor,
                    floor+(o.maxScale-floor)*StyleKit.smoothstep(.12f,.95f,t.dark)));
            }
            if(cp==' ')continue;
            if(o.font instanceof PixelFont5x7 && PixelFont5x7.mask(cp)==0)cp='?';
            // A full-height glyph must leave at least one safe vertical vinyl gap.
            float height=Math.min(c*.92f*scale,c-o.minStrokeMm);
            if(height/7f+1e-5f<o.minStrokeMm){skipped++;continue;}
            ArrayList<float[]> loops=o.font.outline(cp);
            if(loops==null||loops.isEmpty())continue;
            float aspect=o.font.aspect(cp),x0=t.cx-aspect*height/2f,y0=t.cy-height/2f;
            ArrayList<float[]> pending=new ArrayList<>(loops.size());
            boolean fits=true;
            for(float[] loop:loops){CancellationCheckpoint.check();
                float[] out=new float[loop.length];
                for(int p=0;p<loop.length;p+=2){if(((p)&63)==0)CancellationCheckpoint.check();out[p]=x0+loop[p]*height;out[p+1]=y0+loop[p+1]*height;}
                if(needsMaskCheck){
                    for(int p=0;p<out.length;p+=2){if(((p)&63)==0)CancellationCheckpoint.check();
                        int next=(p+2)%out.length;
                        if(!src.validAt(out[p],out[p+1]) ||
                            !src.validAt((out[p]+out[next])/2f,(out[p+1]+out[next+1])/2f)){
                            fits=false;break;
                        }
                    }
                }
                if(!fits)break;
                pending.add(out);
            }
            if(!fits){skipped++;continue;}
            StyleKit.Layer layer;
            if(o.silhouetteUnderlay){
                // First slot belongs ONLY to the filled background. Each glyph is
                // assigned to exactly one upper vinyl sheet, not duplicated across sheets.
                // Distribute text detail by tonal band, dithering transitions so that
                // 2..8 color choices remain useful even with limited source chroma.
                final int textSlots=layers.length-1;
                float band=t.dark*textSlots;
                if(o.orderedDither)band+=(BAYER_4[(t.row&3)*4+(t.col&3)]+.5f)/16f-.5f;
                int slot=1+Math.max(0,Math.min(textSlots-1,(int)Math.floor(band)));
                layer=layers[slot];
            }else layer=palette==null?layers[0]:layers[StyleKit.nearestIndex(t.color,plab)];
            layer.loops.addAll(pending);
            drawn++;
        }
        for(StyleKit.Layer l:layers)if(!l.loops.isEmpty())result.layers.add(l);
        result.summary=(o.mode==Mode.RAMP?"ASCII":"Words")+
            (o.silhouetteUnderlay?" multilayer (snipped base + "+(layers.length-1)+" detail colors)":" portrait")+
            ": "+drawn+
            " cut-safe glyphs, "+nx+" x "+ny+" grid, cell "+String.format(Locale.US,"%.2f",c)+
            " mm"+(skipped>0?" ("+skipped+" rejected for cut width)":"");
        return result;
    }

    private static TextColorSeparation.Analysis analyzeTile(Tile t,int[] palette) {
        if(t.evaluated==null)t.evaluated=TextColorSeparation.analyze(t.rgb35,t.patch,palette,t.edge);
        return t.evaluated;
    }
    static final class Placement {
        final ArrayList<float[]> loops;
        Placement(ArrayList<float[]> loops) { this.loops=loops; }
    }
    /**
     * Transform cuttable pixel-font contours without scaling below the cut-width bound.
     * Validate the actual filled glyph pixels as well as contour edges; otherwise a
     * small removed Snip hole can be inadvertently bridged by a text shape.
     */
    static Placement place(StyleKit.Source src,Options o,Tile tile,int cp,float cell) {
        if(cp==' ')return null;
        long mask=PixelFont5x7.mask(cp);
        if(o.font instanceof PixelFont5x7 && mask==0)return null;
        float height=Math.min(cell*.92f,cell-o.minStrokeMm);
        if(height/7f+1e-5f<o.minStrokeMm)return null;
        float aspect=o.font.aspect(cp);
        float x0=tile.cx-aspect*height/2f,y0=tile.cy-height/2f;
        if(o.font instanceof PixelFont5x7){
            for(int b=0;b<35;b++)if(((mask>>>b)&1L)!=0){
                float x=x0+((b%5)+.5f)*height/7f;
                float y=y0+((b/5)+.5f)*height/7f;
                if(!src.validAt(x,y))return null;
            }
        }
        ArrayList<float[]> raw=o.font.outline(cp);
        if(raw==null||raw.isEmpty())return null;
        ArrayList<float[]> polygons=new ArrayList<>(raw.size());
        for(float[] loop:raw){CancellationCheckpoint.check();
            if(loop.length<6)return null;
            float[] out=new float[loop.length];
            for(int p=0;p<loop.length;p+=2){if(((p)&63)==0)CancellationCheckpoint.check();
                out[p]=x0+loop[p]*height;out[p+1]=y0+loop[p+1]*height;
                int next=(p+2)%loop.length;
                float mx=x0+(loop[p]+loop[next])*.5f*height;
                float my=y0+(loop[p+1]+loop[next+1])*.5f*height;
                if(!src.validAt(out[p],out[p+1])||!src.validAt(mx,my))return null;
            }
            polygons.add(out);
        }
        return new Placement(polygons);
    }
    private static final class WordCandidate {
        final String word;
        final int length,slot,tokenIndex;
        final float score;
        final ArrayList<float[]> outlines;
        WordCandidate(String word,int length,int slot,int tokenIndex,float score,ArrayList<float[]> outlines) {
            this.word=word;this.length=length;this.slot=slot;this.tokenIndex=tokenIndex;this.score=score;this.outlines=outlines;
        }
    }
    private static String[] wordTokens(String text) {
        String upper=text.toUpperCase(Locale.ROOT);
        String[] parts=upper.split("[^A-Z0-9]+");
        ArrayList<String> out=new ArrayList<>();
        for(String token:parts)if(!token.isEmpty()&&token.length()<=32)out.add(token);
        return out.toArray(new String[0]);
    }
    /** RC62: Shared SLIC-like color regions drive two independent text engines. */
    private static void renderColorComposite(StyleKit.Source src,Options o,
          ArrayList<Tile> tiles,StyleKit.Layer[] layers,float cell,int nx,int ny,StyleKit.Result result) {
        // Knockout uses its own bounded physical color-plate raster; do not
        // waste time creating SLIC regions that it does not consume.
        TextPortraitRegions.Map regions=(o.mode==Mode.WORDS&&o.wordStyle==WordStyle.KNOCKOUT)
            ?null:TextPortraitRegions.analyze(src,cell);
        if(o.mode==Mode.RAMP)
            result.summary=AsciiPortraitEngine.render(src,o,tiles,regions,layers,cell,nx,ny);
        else
            result.summary=o.wordStyle==WordStyle.KNOCKOUT
                ?WordKnockoutEngine.render(src,o,regions,layers,cell)
                :WordPortraitEngine.render(src,o,regions,layers,cell);
        for(StyleKit.Layer layer:layers)if(!layer.loops.isEmpty())result.layers.add(layer);
    }
}

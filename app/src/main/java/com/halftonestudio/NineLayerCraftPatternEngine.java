package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * RC168: fixed-node knotted fishnet and row-accurate filet crochet; RC167 camouflage preserved.
 * L1 is the solid physical Snip silhouette; L2..L9 contain actual unioned blade geometry.
 * All work is interruptible, all coordinates are millimeters, and SVG tracing is shared.
 */
public final class NineLayerCraftPatternEngine {
    private NineLayerCraftPatternEngine() { }
    // Do not renumber these: the integer is derived from the persisted VinylStyleSuite ID.
    public static final int CAMOUFLAGE=0, LEAF_VEIN=1, FISHNET=2, LACE=3, CROCHET=4,
        CAMO_DESERT=5, CAMO_URBAN=6, CAMO_DIGITAL=7;
    public static final String[] NAMES={"Woodland Camouflage","Hierarchical Leaf Venation",
        "Knotted Fishnet","Connected Floral Lace","Interlocked Filet Crochet",
        "Desert Camouflage","Urban Camouflage","Digital Camouflage"};
    public static boolean camouflageType(int pattern){return pattern==CAMOUFLAGE||
        pattern==CAMO_DESERT||pattern==CAMO_URBAN||pattern==CAMO_DIGITAL;}

    public static final class Options {
        public int pattern=CAMOUFLAGE, detail=58, layers=9;
        public float widthMm=152.4f, minCutMm=.254f;
        public float gapMm=.5f;
        public boolean draft=false;
        /** M5: raster de-staircasing of organic camouflage contours (0 = off, the default until
         * the residual 0.15 mm foundation slivers are closed; see M5-CROCHET-SMOOTHING.md). Not used by
         * Digital camo (pixel-square by design) or the textile patterns. */
        public int contourSmoothing=0;
        public boolean[] allowed;
        public int[] palette;
        public int paperArgb=0xffffffff;
        public void validate() {
            if(pattern<0||pattern>=NAMES.length||detail<0||detail>100||layers<2||layers>9||
                !Float.isFinite(widthMm)||widthMm<20||widthMm>2000||
                !Float.isFinite(minCutMm)||minCutMm<.1f||minCutMm>4||
               !Float.isFinite(gapMm)||gapMm<=0||
                (palette!=null&&palette.length!=layers)||
                contourSmoothing<0||contourSmoothing>OrganicFlowCutSmoother.MAX)
                throw new IllegalArgumentException("Invalid RC164 craft settings");
        }
    }
    static final class Cell {
        final float x,y,lum,contrast;
        final long key;
        final int row,col;
        final boolean perimeter;
        final int toneRow,toneCol;
        int layer;
        int rgb;
        double perceptualTone;
        Cell(float x,float y,float lum,float contrast,long key,int row,int col) {
            this(row,col,x,y,lum,contrast,key,false,row,col);
        }
        Cell(int row,int col,float x,float y,float lum,float contrast,long key,
             boolean perimeter,int toneRow,int toneCol){
            this.x=x;this.y=y;this.lum=lum;this.contrast=contrast;this.key=key;
            this.row=row;this.col=col;this.perimeter=perimeter;
            this.toneRow=toneRow;this.toneCol=toneCol;
        }
    }
    static long mix(long x) {
        x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;
        x=(x^(x>>>27))*0x94d049bb133111ebL;
        return x^(x>>>31);
    }
    static float u01(long seed){return (mix(seed)>>>40)*0x1.0p-24f;}
    static float smooth(float f){return f*f*(3-2*f);}
    static float fract(float f){return f-(float)Math.floor(f);}
    /** Smooth spatial noise gives contiguous camouflage regions rather than scattered cell islands. */
    static float field(float x,float y,float scale,long salt){
        float fx=x/scale,fy=y/scale;
        int ix=(int)Math.floor(fx),iy=(int)Math.floor(fy);
        float a=smooth(fract(fx)),b=smooth(fract(fy));
        float n00=u01(salt^((long)ix*0x9e3779b97f4a7c15L)^((long)iy*0xd1b54a32d192ed03L));
        float n10=u01(salt^((long)(ix+1)*0x9e3779b97f4a7c15L)^((long)iy*0xd1b54a32d192ed03L));
        float n01=u01(salt^((long)ix*0x9e3779b97f4a7c15L)^((long)(iy+1)*0xd1b54a32d192ed03L));
        float n11=u01(salt^((long)(ix+1)*0x9e3779b97f4a7c15L)^((long)(iy+1)*0xd1b54a32d192ed03L));
        return (n00*(1-a)+n10*a)*(1-b)+(n01*(1-a)+n11*a)*b;
    }
    static float normalizedVariance(StyleKit.Source src,float x,float y,float step) {
        float mid=src.lumAt(x,y);
        float dx=Math.abs(src.lumAt(Math.max(0,x-step),y)-src.lumAt(Math.min(src.widthMm,x+step),y));
        float dy=Math.abs(src.lumAt(x,Math.max(0,y-step))-src.lumAt(x,Math.min(src.heightMm,y+step)));
        return StyleKit.clampF((dx+dy)*1.5f+Math.abs(mid-.5f)*.09f,0,1);
    }
    /** A gently tension-warped net: every strand and knot uses identical node coordinates. */
    static float fishnetNodeX(int row,int col,float pitch){
        float x=(col+.5f+((row&1)*.5f))*pitch;
        float y=(row+.5f)*pitch*.80f;
        return x+(field(x,y,pitch*5.3f,0x81a72817L)-.5f)*pitch*.26f;
    }
    static float fishnetNodeY(int row,int col,float pitch){
        float x=(col+.5f+((row&1)*.5f))*pitch;
        float y=(row+.5f)*pitch*.80f;
        return y+(field(x,y,pitch*4.7f,0x8b61d352L)-.5f)*pitch*.19f;
    }
    static ArrayList<Cell> createCells(StyleKit.Source s,Options o,float pitch) {
        ArrayList<Cell> cells=new ArrayList<>();
        float pitchY=o.pattern==FISHNET?pitch*.80f:o.pattern==CROCHET?pitch*.85f:pitch;
        int rows=(int)Math.ceil(s.heightMm/pitchY)+1;
        int cols=(int)Math.ceil(s.widthMm/pitch)+1;
        for(int row=(o.pattern==FISHNET?-1:0);row<rows;row++) {
            if((row&7)==0)CancellationCheckpoint.check();
            for(int col=(o.pattern==FISHNET?-1:0);col<cols;col++) {
                long h=mix(0x6a08e667f3bcc909L^((long)row<<32)^col);
                float jitter=(o.pattern==FISHNET||o.pattern==CROCHET||o.pattern==LACE)?0f:.17f;
                float stagger=o.pattern==FISHNET?((row&1)*.5f):0f; // Crochet must align columns.
                float x=(col+.5f+stagger+jitter*(u01(h)-.5f))*pitch;
                float y=(row+.5f+jitter*(u01(h+1)-.5f))*pitchY;
                if(o.pattern==FISHNET){x=fishnetNodeX(row,col,pitch);y=fishnetNodeY(row,col,pitch);}
                boolean perimeter=false;
                int toneRow=row,toneCol=col;
                float sampleX=x,sampleY=y;
                if(o.pattern==FISHNET){
                    // Parents just outside the Snip supply incoming strands. Their
                    // color must come from a REAL subject node, not the outside pixels.
                    if(x < -pitch*1.5f||y < -pitch*1.5f||
                        x >= s.widthMm+pitch*1.5f||y >= s.heightMm+pitch*1.5f)continue;
                    if(!s.validAt(x,y)){
                        perimeter=true;
                        float best=Float.POSITIVE_INFINITY;
                        for(int side=-1;side<=1;side+=2){
                            int childCol=col+(((row&1)==0)?(side>0?0:-1):(side>0?1:0));
                            float childX=fishnetNodeX(row+1,childCol,pitch);
                            float childY=fishnetNodeY(row+1,childCol,pitch);
                            if(!s.validAt(childX,childY))continue;
                            float d=(childX-x)*(childX-x)+(childY-y)*(childY-y);
                            if(d<best){best=d;toneRow=row+1;toneCol=childCol;
                                sampleX=childX;sampleY=childY;}
                        }
                        if(best==Float.POSITIVE_INFINITY)continue;
                    }
                }else if(x<0||y<0||x>=s.widthMm||y>=s.heightMm||!s.validAt(x,y))continue;
                float edge=normalizedVariance(s,sampleX,sampleY,pitch*.65f);
                float lum=camouflageType(o.pattern)?camouflageTone(s,x,y,pitch,edge,o.pattern):
                    (o.pattern==FISHNET||o.pattern==CROCHET)?textileTone(s,sampleX,sampleY,pitch):s.lumAt(x,y);
                Cell cell=new Cell(row,col,x,y,lum,edge,h,perimeter,toneRow,toneCol);
                cell.rgb=s.colorAt(sampleX,sampleY);
                cell.perceptualTone=PerceptualLayerColorEngine.light(cell.rgb)+.45*(lum-s.lumAt(sampleX,sampleY));
                cells.add(cell);
            }
        }
        return cells;
    }
    /** Preserve small portrait edges in threaded patterns without false random tones. */
    static float textileTone(StyleKit.Source s,float x,float y,float pitch){
        float raw=s.lumAt(x,y);
        float r=Math.max(s.mmPerPx,pitch*.35f);
        float neighbors=(s.lumAt(x-r,y)+s.lumAt(x+r,y)+
            s.lumAt(x,y-r)+s.lumAt(x,y+r))*.25f;
        return StyleKit.clampF(raw+(raw-neighbors)*.48f,0,1);
    }
    /** Finer real stitch/mesh pitch with bounded cost during slider dragging. */
    static float textilePitch(StyleKit.Source s,Options o){
        float pitch=Math.max(o.minCutMm*6.6f,(6.55f-.0285f*o.detail)*
            (float)Math.sqrt(s.widthMm/279.4f));
        if(o.draft)pitch*=1.22f;
        double cells=((double)s.widthMm/pitch+2d)*((double)s.heightMm/(pitch*.82f)+2d);
        int cap=o.draft?4300:8400;
        if(cells>cap)pitch*=(float)Math.sqrt(cells/cap);
        return pitch;
    }
    /**
     * Edge-preserving two-scale camo tone map: keep the photograph in control of
     * eyes, nose and mouth. Texture perturbs smooth regions only; it must never
     * replace the underlying photographic luminance (RC164 overmixed it).
     * The high-pass term restores features softened by the image pyramid.
     */
    static float camouflageTone(StyleKit.Source s,float x,float y,float pitch,float edge,int pattern){
        float raw=s.lumAt(x,y);
        float radius=Math.max(s.mmPerPx*.9f,pitch*.47f);
        float neighbors=(s.lumAt(x-radius,y)+s.lumAt(x+radius,y)+
            s.lumAt(x,y-radius)+s.lumAt(x,y+radius))*.25f;
        float detail=StyleKit.clampF((raw-neighbors)*.43f,-.12f,.12f);
        float macro=field(x,y,Math.max(pitch*7.0f,13f),0x40736ab2L)-.5f;
        float medium=field(x,y,Math.max(pitch*3.0f,7.0f),0x6820c1a4L)-.5f;
        float noise=(macro*.72f+medium*.28f);
        float strength=pattern==CAMO_DIGITAL?.085f:
            pattern==CAMO_DESERT?.105f:pattern==CAMO_URBAN?.115f:.145f;
        // Preserve the original edge rather than moving it into a random color region.
        float edgeProtection=1f-.82f*StyleKit.clampF(edge*3.4f,0f,1f);
        return StyleKit.clampF(raw+detail+noise*strength*edgeProtection,0f,1f);
    }
    /**
     * Fine sampling is necessary for 11-inch portraits; old 5-6 mm cells erase
     * the eyes and mouth. Scale pitch sublinearly with sheet size to cap work.
     * Drafts deliberately use fewer cells; export remains full-resolution.
     */
    static float camouflagePitch(StyleKit.Source s,Options o){
        float fine=Math.max(o.minCutMm*5.2f,(5.35f-.027f*o.detail)*
            (float)Math.sqrt(s.widthMm/279.4f));
        if(o.draft)fine*=1.27f;
        // Bounded workload for large posters, tall input photos, and min-cut extremes.
        double cells=((double)s.widthMm/fine+1d)*((double)s.heightMm/fine+1d);
        int cap=o.draft?6000:14000;
        if(cells>cap)fine*=(float)Math.sqrt(cells/cap);
        return fine;
    }
    /**
     * Population-balanced photographic quantiles. Stable tie breaking retains all overlay
     * sheets on flat photos. The physical L1 stays outside the quantile assignment.
     */
    static void assignQuantiles(ArrayList<Cell> cells,int[] palette,int layers) {
        ArrayList<Cell> subjects=new ArrayList<>(cells.size());
        for(Cell c:cells)if(!c.perimeter)subjects.add(c);
        if(subjects.size()<layers-1)throw new IllegalArgumentException(
            "Not enough photo area inside Snip for all requested vinyl layers");
        Integer[] byTone=new Integer[layers-1];
        for(int i=0;i<byTone.length;i++)byTone[i]=i+1;
        Arrays.sort(byTone,(a,b)->Double.compare(
            PerceptualVinylColor.lab(palette[a])[0],PerceptualVinylColor.lab(palette[b])[0]));
        subjects.sort((a,b)->{
            int cmp=Double.compare(a.perceptualTone,b.perceptualTone);
            return cmp!=0?cmp:Long.compareUnsigned(a.key,b.key);
        });
        int overlays=layers-1;
        java.util.HashMap<Long,Cell> byPosition=new java.util.HashMap<>(subjects.size()*2);
        for(int i=0;i<subjects.size();i++) {
            if((i&1023)==0)CancellationCheckpoint.check();
            int quantile=Math.min(overlays-1,(int)((long)i*overlays/subjects.size()));
            Cell c=subjects.get(i);
            c.layer=byTone[quantile];
            byPosition.put((((long)c.row)<<32)^(c.col&0xffffffffL),c);
        }
        for(Cell c:cells)if(c.perimeter){
            Cell anchor=byPosition.get((((long)c.toneRow)<<32)^(c.toneCol&0xffffffffL));
            // Every perimeter parent has at least one valid immediate child.
            if(anchor==null)throw new IllegalStateException("Missing fishnet perimeter tone anchor");
            c.layer=anchor.layer;
        }
        // Restore the prior deterministic render order after quantile assignment.
        cells.sort((a,b)->{
            int cmp=Float.compare(a.lum,b.lum);
            return cmp!=0?cmp:Long.compareUnsigned(a.key,b.key);
        });
    }
    /** Perceptual correction with minimum sheet population for textile/camouflage cells.
     * No sheet may become empty, and Snip-exterior net anchors inherit the
     * SAME owner as their interior child (never a background photograph color). */
    static void refineOverlaySwatches(ArrayList<Cell> cells,int[] palette,int layers){
        if(layers<=2)return;
        ArrayList<Cell> subject=new ArrayList<>();
        for(Cell c:cells)if(!c.perimeter)subject.add(c);
        if(subject.size()<layers*2)return;
        int[] source=new int[subject.size()];
        for(int i=0;i<source.length;i++)source[i]=subject.get(i).rgb;
        int[] overlays=Arrays.copyOfRange(palette,1,layers);
        PhotoSwatchAssociation association=new PhotoSwatchAssociation(
            overlays,overlays.length,null,source,null,PhotoSwatchAssociation.TEXTILES);
        int[] counts=new int[layers];for(Cell c:subject)counts[c.layer]++;
        // Bound geometry complexity per physical sheet. Closest color may take
        // up to 1.65x its equal-share motif budget; do not create one huge union.
        int max=(int)Math.ceil(subject.size()/(double)(layers-1)*1.65);
        for(Cell c:subject){
            int old=c.layer-1;
            int proposed=association.choose(c.rgb);
            if(proposed==old||counts[old+1]<=2||counts[proposed+1]>=max)continue;
            double oldD=PerceptualLayerColorEngine.error(c.rgb,overlays[old]);
            double newD=PerceptualLayerColorEngine.error(c.rgb,overlays[proposed]);
            // Require a clear genuine improvement to avoid noisy micro-islands.
            if(newD<oldD*.75){counts[old+1]--;counts[proposed+1]++;c.layer=proposed+1;}
        }
        java.util.HashMap<Long,Cell> owners=new java.util.HashMap<>();
        for(Cell c:subject)owners.put((((long)c.row)<<32)^(c.col&0xffffffffL),c);
        for(Cell c:cells)if(c.perimeter){
            Cell owner=owners.get((((long)c.toneRow)<<32)^(c.toneCol&0xffffffffL));
            if(owner==null)throw new IllegalStateException("Missing net tone anchor");
            c.layer=owner.layer;
        }
    }
    static int[] autoColors(StyleKit.Source s,int layers) {
        int[] seed=FallenConfettiEngine.autoPalette(s,8);
        int[] colors=new int[layers];
        colors[0]=FoundationColorPolicy.bestColor(seed,8,null,false);
        for(int i=1;i<layers;i++){
            float pos=(i-1f)/Math.max(1,layers-2);
            float idx=pos*7f;
            int ai=seed[Math.min(7,(int)Math.floor(idx))],bi=seed[Math.min(7,(int)Math.ceil(idx))];
            float t=idx-(int)Math.floor(idx);
            colors[i]=blend(ai,bi,t);
        }
        return colors;
    }
    private static int blend(int a,int b,float t) {
        int r=Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t);
        int g=Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t);
        int bl=Math.round((a&255)*(1-t)+(b&255)*t);
        return 0xff000000|(r<<16)|(g<<8)|bl;
    }
    static float safeStroke(PatternPortraitTrioEngine.Surface surface,Options o) {
        return Math.max(o.minCutMm,surface.mm*2.65f);
    }
    public static StyleKit.Result generate(int[] argb,int w,int h,Options o) {
        if(o==null)throw new IllegalArgumentException("Options required");
        o.validate();
        StyleKit.Source s=new StyleKit.Source(argb,w,h,o.allowed,o.widthMm).autoLevels(.02f,.98f);
        if(!s.anyValid())throw new IllegalArgumentException("Subject silhouette is empty");
        if(o.pattern==CROCHET)return FiletCrochet.generate(s,o);
        float pitch=camouflageType(o.pattern)?camouflagePitch(s,o):
            (o.pattern==FISHNET||o.pattern==CROCHET)?textilePitch(s,o):
            Math.max(o.minCutMm*6.2f,8.1f-o.detail*.037f);
        if(o.pattern==LEAF_VEIN)pitch*=.98f;
        ArrayList<Cell> cells=createCells(s,o,pitch);
        PatternPortraitTrioEngine.Surface surface=new PatternPortraitTrioEngine.Surface(s,o.layers,o.minCutMm,o.gapMm,o.draft,o.pattern==FISHNET);
        int[] colors=o.palette==null?autoColors(s,o.layers):o.palette.clone();
        surface.fillValid(0); // Single protected, fully filled physical sheet; never a phantom 10th layer.
        assignQuantiles(cells,colors,o.layers);
        if(!camouflageType(o.pattern)||o.pattern==CAMO_URBAN)refineOverlaySwatches(cells,colors,o.layers);
        int[] assignmentCounts=new int[o.layers];
        float stroke=safeStroke(surface,o);
        if(o.pattern==LEAF_VEIN)leafScaffold(surface,pitch,stroke,o);
        for(int i=0;i<cells.size();i++){
            if((i&63)==0)CancellationCheckpoint.check();
            Cell cell=cells.get(i);if(!cell.perimeter)assignmentCounts[cell.layer]++;
            switch(o.pattern){
                case CAMOUFLAGE:case CAMO_DESERT:case CAMO_URBAN:case CAMO_DIGITAL:
                    camouflage(surface,cell,pitch,o);break;
                case LEAF_VEIN:veins(surface,cell,pitch,stroke,o);break;
                case FISHNET:fishnet(surface,cell,pitch,stroke,o);break;
                case LACE:lace(surface,cell,pitch,stroke,o);break;
                case CROCHET:crochet(surface,cell,pitch,stroke,o);break;
                default:throw new IllegalStateException("Unrecognized pattern");
            }
        }
        if(camouflageType(o.pattern)&&o.pattern!=CAMO_URBAN){
            resolveCamoOcclusion(surface,cells,colors,pitch,o);
            // Conditioning is the existing physical-mm close/open used for
            // cuttable organic masks. Keep digital block cleanup orthogonal.
            OrganicFlowPortraitEngine.conditionMasks(surface,o.minCutMm,o.gapMm,o.pattern==CAMO_DIGITAL);
        }
        int subjectMotifs=0;for(Cell cell:cells)if(!cell.perimeter)subjectMotifs++;
        StyleKit.Result result=surface.finish(colors,"craft-"+o.pattern+"-layer",subjectMotifs,o.minCutMm);
        // M5: organic camouflage patches are traced from a binary raster; remove the pixel
        // staircase uniformly on every overlay sheet (bounded, verified; L1 untouched).
        // Digital camouflage is pixel-square by design and is never smoothed.
        int destaired=0;
        if(camouflageType(o.pattern)&&o.pattern!=CAMO_DIGITAL&&!surface.vectorEnabled())
            destaired=OrganicFlowCutSmoother.destairOrganicContours(result,surface.valid,surface.ink,surface.w,surface.h,
                surface.mm,o.minCutMm,o.gapMm,o.contourSmoothing);
        if(!surface.vectorEnabled()&&!o.draft&&o.pattern==FISHNET)
            OrganicFlowCutSmoother.polishRasterMotifs(result,surface,o.minCutMm,o.gapMm);
        result.paperArgb=o.paperArgb;
        for(int l=0;l<result.layers.size();l++){
            if(result.layers.get(l).loops.isEmpty())
                throw new IllegalStateException("L"+(l+1)+" has no cuttable geometry; increase size or reduce layers");
        }
        result.summary=NAMES[o.pattern]+" · solid L1 + "+(o.layers-1)+
            " photo-balanced registered cut overlays · "+subjectMotifs+" motifs · "+o.layers+
            " real physical vinyl layers · overlay motifs "+
            Arrays.toString(Arrays.copyOfRange(assignmentCounts,1,assignmentCounts.length))+
            " · min cut "+o.minCutMm+" mm";
        if(surface.vectorEnabled())result.summary+=" · independent vector Boolean overlays";
        if(destaired>0)result.summary+=" · "+destaired+" contours de-staircased";
        return result;
    }
    /** Choose the visible color from competing patches by local tone rank.
     * Keep hidden lower vinyl; remove only upper colors that obscure detail. */
    static void resolveCamoOcclusion(PatternPortraitTrioEngine.Surface surface,ArrayList<Cell> cells,
            int[] palette,float pitch,Options o){
        if(o.layers<3||cells.size()<o.layers*2)return;
        float[] sorted=new float[cells.size()];int n=0;
        for(Cell c:cells)sorted[n++]=c.lum;
        Arrays.sort(sorted);
        if(sorted[(int)(n*.95f)]-sorted[(int)(n*.05f)]<.06f)return;
        Integer[] slots=new Integer[o.layers-1];for(int i=0;i<slots.length;i++)slots[i]=i+1;
        Arrays.sort(slots,(a,b)->Double.compare(PerceptualVinylColor.lab(palette[a])[0],PerceptualVinylColor.lab(palette[b])[0]));
        float[] thresholds=new float[slots.length-1];
        for(int i=0;i<thresholds.length;i++)thresholds[i]=sorted[Math.min(n-1,(i+1)*n/slots.length)];
        int step=Math.max(1,Math.round(Math.max(o.minCutMm*1.7f,pitch*.22f)/surface.mm));
        for(int y=0;y<surface.h;y+=step){CancellationCheckpoint.check();
            for(int x=0;x<surface.w;x+=step){
                float mmX=Math.min(surface.w-.5f,x+step*.5f)*surface.mm;
                float mmY=Math.min(surface.h-.5f,y+step*.5f)*surface.mm;
                float tone=surface.src.lumAt(mmX,mmY);int rank=0;
                while(rank<thresholds.length&&tone>thresholds[rank])rank++;
                for(int yy=y;yy<Math.min(surface.h,y+step);yy++)for(int xx=x;xx<Math.min(surface.w,x+step);xx++){
                    int ix=yy*surface.w+xx;if(!surface.valid[ix])continue;
                    int best=-1,score=Integer.MAX_VALUE;
                    for(int r=0;r<slots.length;r++)if(surface.ink[slots[r]][ix]){
                        int d=Math.abs(rank-r);if(d<score||(d==score&&slots[r]>best)){best=slots[r];score=d;}}
                    if(best>=0)for(int l=best+1;l<o.layers;l++)surface.ink[l][ix]=false;
                }
            }
        }
    }

    /** Multi-scale clipped organic patches, angular city breakup, or pixel-cluster regions. */
    private static void camouflage(PatternPortraitTrioEngine.Surface surface,Cell c,float pitch,Options o){
        if(o.pattern==CAMO_DIGITAL){
            // Stair-stepped connected orthogonal clusters, not isolated single-pixel dots.
            float unit=Math.max(o.minCutMm*3.2f,pitch*(.33f-.10f*StyleKit.clampF(c.contrast*2.8f,0f,1f)));
            float x=(float)Math.floor(c.x/unit)*unit,y=(float)Math.floor(c.y/unit)*unit;
            float ww=unit*(3f+(int)(u01(c.key+4)*3)),hh=unit*(2f+(int)(u01(c.key+5)*3));
            block(surface,c.layer,x-ww*.5f,y-hh*.5f,ww,hh);
            block(surface,c.layer,x+ww*.15f,y-hh*.5f-unit,unit*2f,unit*2f);
            if(u01(c.key+9)>.57f)block(surface,c.layer,x-ww*.38f-unit,y+hh*.04f,unit*2f,unit*2f);
            return;
        }
        boolean urban=o.pattern==CAMO_URBAN;
        boolean desert=o.pattern==CAMO_DESERT;
        // Broad linked camouflage in low-detail regions; compact boundaries
        // along real photo features so fine contours remain recognizable.
        float feature=StyleKit.clampF(c.contrast*2.8f,0f,1f);
        float extent=1.25f-.35f*feature;
        float rx=pitch*extent*(urban?.85f:desert?1.27f:1.08f);
        float ry=pitch*extent*(urban?.77f:desert?.73f:.93f);
        float theta=urban?(float)(Math.PI*.25f*(int)(u01(c.key+7)*4)):
            desert?.12f*(u01(c.key+7)-.5f):(u01(c.key+7)-.5f)*.72f;
        float sn=(float)Math.sin(theta),cs=(float)Math.cos(theta);
        int vertices=urban?7:12;
        float[] polygon=new float[vertices*2];
        float spread=urban?.18f:desert?.27f:.34f;
        for(int i=0;i<vertices;i++){
            float angle=(float)(2*Math.PI*i/vertices);
            // Wavy multi-lobed outline; spatially coherent regions overlap and union on one sheet.
            float lobe=(float)Math.sin(angle*3+u01(c.key+15)*6.2831853)*.11f;
            float radial=1+lobe+spread*(u01(c.key+30+i)-.5f);
            float a=rx*(float)Math.cos(angle)*radial,b=ry*(float)Math.sin(angle)*radial;
            polygon[2*i]=c.x+cs*a-sn*b;
            polygon[2*i+1]=c.y+sn*a+cs*b;
        }
        surface.polygon(c.layer,polygon);
        if(!urban&&u01(c.key+91)>.58f){
            // Adjoining lobes break up the original rounded-cell appearance.
            float dx=rx*.58f*(u01(c.key+92)>.5f?1:-1);
            float dy=ry*.42f*(u01(c.key+93)-.5f);
            surface.disk(c.layer,c.x+dx,c.y+dy,Math.max(o.minCutMm*.75f,ry*.55f));
        }
    }
    private static void block(PatternPortraitTrioEngine.Surface s,int layer,float x,float y,float w,float h){
        s.polygon(layer,new float[]{x,y,x+w,y,x+w,y+h,x,y+h});
    }
    private static float midribX(StyleKit.Source s,float y){
        float t=StyleKit.clampF(y/s.heightMm,0,1);
        return s.widthMm*(.50f+.043f*(float)Math.sin((1-t)*Math.PI*1.6));
    }
    /** One continuous ascending midrib, opposing arcuate secondary branches, tapered thickness. */
    private static void leafScaffold(PatternPortraitTrioEngine.Surface surface,float pitch,float stroke,Options o){
        StyleKit.Source src=surface.src;
        final int l=1;
        float dy=Math.max(pitch*.64f,src.heightMm/90f);
        for(float y=src.heightMm-dy;y>0;y-=dy){
            CancellationCheckpoint.check();
            float w=stroke*(1.3f+.75f*y/src.heightMm);
            surface.segment(l,midribX(src,y+dy),y+dy,midribX(src,y),y,w);
        }
        float separation=pitch*3.5f;
        for(float y=src.heightMm-separation*.8f;y>pitch;y-=separation){
            CancellationCheckpoint.check();
            for(int side:new int[]{-1,1}){
                float origin=midribX(src,y);
                float reach=src.widthMm*(.33f+.09f*u01((long)(y*53)+side*79L));
                float xEnd=origin+side*reach;
                float yEnd=Math.max(0,y-separation*(1.2f+.7f*u01((long)(y*11)+side*47L)));
                float cx=origin+side*reach*.50f,cy=y-separation*.20f;
                float px=origin,py=y;
                for(int i=1;i<=8;i++){
                    float t=i/8f,u=1-t;
                    float nx=u*u*origin+2*u*t*cx+t*t*xEnd;
                    float ny=u*u*y+2*u*t*cy+t*t*yEnd;
                    float thickness=stroke*(1.28f-.38f*t);
                    surface.segment(l,px,py,nx,ny,thickness);
                    px=nx;py=ny;
                }
            }
        }
    }
    /** Reticulating capillaries, following branching vector field rather than repeated combs. */
    private static void veins(PatternPortraitTrioEngine.Surface surface,Cell c,float pitch,float stroke,Options o){
        float xMid=midribX(surface.src,c.y);
        float side=c.x<xMid?-1f:1f;
        float across=Math.abs(c.x-xMid)/Math.max(1,surface.src.widthMm*.5f);
        float a=-.55f-.48f*across+(.22f*(u01(c.key+4)-.5f));
        float len=pitch*(.68f+.33f*c.contrast);
        float x1=c.x+side*len*(float)Math.cos(a),y1=c.y+len*(float)Math.sin(a);
        float w=Math.max(stroke*.70f,o.minCutMm);
        float bend=.18f*pitch*(u01(c.key+9)-.5f);
        curved(surface,c.layer,c.x,c.y,c.x+side*len*.50f,c.y-len*.22f+bend,x1,y1,w,4);
        // Cross venules connect lateral lines into a netted botanical fabric.
        if(u01(c.key+13)>.18f){
            float bx=c.x+side*pitch*.18f,by=c.y+pitch*.62f;
            curved(surface,c.layer,c.x,c.y,c.x+side*pitch*.04f,c.y+pitch*.37f,bx,by,w*.92f,3);
        }
        if(u01(c.key+20)>.63f){
            float bx=c.x-side*pitch*.17f,by=c.y-pitch*.48f;
            curved(surface,c.layer,c.x,c.y,c.x-side*pitch*.12f,c.y-pitch*.21f,bx,by,w*.88f,3);
        }
    }
    /**
     * Diamond mesh built from one set of staggered knot coordinates.
     * Each vertex emits ONLY its two downward strands: no duplicated geometry
     * across opposite directions, so separate color sheets cannot double-cut the
     * same cord. Knot winding is a compact fused vinyl shape, not a drilled hole.
     */
    static void fishnet(PatternPortraitTrioEngine.Surface surface,Cell c,float pitch,float stroke,Options o){
        float cord=Math.max(o.minCutMm,stroke*(.79f+.32f*(1-c.lum)+.18f*c.contrast));
        // Both endpoints come from one shared lattice, not duplicated point guesses.
        for(int side:new int[]{-1,1}){
            int nextCol=c.col+(((c.row&1)==0)?(side>0?0:-1):(side>0?1:0));
            float ex=fishnetNodeX(c.row+1,nextCol,pitch);
            float ey=fishnetNodeY(c.row+1,nextCol,pitch);
            float midX=c.x+(ex-c.x)*.5f;
            float midY=c.y+(ey-c.y)*.5f+pitch*(.04f+.07f*c.contrast);
            curved(surface,c.layer,c.x,c.y,midX,midY,ex,ey,cord,5);
        }
        // Each intersection is a rounded, slightly oblong wrapped junction.
        float knot=Math.max(o.minCutMm*.80f,cord*(1.05f+.16f*c.contrast));
        surface.disk(c.layer,c.x,c.y,knot);
        surface.segment(c.layer,c.x-knot*.66f,c.y-knot*.22f,
            c.x+knot*.62f,c.y+knot*.22f,Math.max(o.minCutMm,cord*.70f));
        surface.disk(c.layer,c.x-knot*.27f,c.y+knot*.22f,knot*.49f);
    }
    /** Alternating floral motifs, leaf appliques, and connected mesh, not a rosette stamp grid. */
    private static void lace(PatternPortraitTrioEngine.Surface surface,Cell c,float pitch,float stroke,Options o){
        float yarn=Math.max(o.minCutMm,stroke*(.88f+.19f*(1-c.lum)));
        float r=pitch*.42f;
        // Consistent supporting ground across all cells, with arches between junctions.
        curved(surface,c.layer,c.x-r,c.y,c.x,c.y-r*.6f,c.x+r,c.y,yarn*.87f,4);
        curved(surface,c.layer,c.x,c.y-r,c.x+r*.60f,c.y,c.x,c.y+r,yarn*.87f,4);
        float style=u01(c.key+281);
        if(style<.18f){
            // Main six-petal floral motif with linked center and overlapping petals.
            float petal=pitch*.26f;
            surface.disk(c.layer,c.x,c.y,yarn*.92f);
            for(int i=0;i<6;i++){
                float a=(float)(2*Math.PI*i/6),x=c.x+petal*(float)Math.cos(a),y=c.y+petal*(float)Math.sin(a);
                surface.segment(c.layer,c.x,c.y,x,y,yarn);
                surface.ellipseRing(c.layer,x,y,petal*.57f,petal*.47f,a,
                    Math.max(yarn*.56f,surface.mm*1.5f));
            }
        }else if(style<.43f){
            // Curved leaf / teardrop with a central gimp stroke.
            float dir=u01(c.key+15)>.5f?1f:-1f;
            float ex=c.x+dir*r*.98f,ey=c.y-r*.60f;
            curved(surface,c.layer,c.x,c.y,c.x+dir*r*.35f,c.y-r*.84f,ex,ey,yarn*1.25f,5);
            curved(surface,c.layer,c.x,c.y,c.x+dir*r*.8f,c.y+r*.21f,ex,ey,yarn,5);
        }else if(style<.62f){
            // Openwork picot-like loop, bridged back into the supporting mesh.
            surface.ellipseRing(c.layer,c.x,c.y,r*.53f,r*.53f,0,
                Math.max(yarn*.6f,surface.mm*1.4f));
            surface.segment(c.layer,c.x-r*.5f,c.y,c.x+r*.5f,c.y,yarn*.95f);
        }
    }
    /**
     * Single-cell filet stitch (kept for primitive tests and previews): an open mesh
     * for light cells, a solid block for dark cells, drawn with the same pixel-exact
     * lattice rules as {@link FiletCrochet}. Full artwork uses {@link FiletCrochet}.
     */
    static void crochet(PatternPortraitTrioEngine.Surface surface,Cell c,float pitch,float stroke,Options o){
        FiletCrochet.Lattice g=FiletCrochet.Lattice.single(surface,c.x,c.y,pitch,o);
        if(g==null)return;
        int state=c.lum<.49f?FiletCrochet.BLOCK:FiletCrochet.MESH;
        FiletCrochet.drawCell(surface,g,0,0,c.layer,state,new int[][]{{c.layer}},new int[][]{{state}});
    }
    /**
     * M5 filet crochet (style 39). Filet crochet is a square grid: every square is
     * framed by double-crochet posts (vertical) and chain bars (horizontal), and is
     * either an open MESH (chain space), a HALF block (one extra post splitting the
     * space; only where both spaces stay weedable) or a solid BLOCK.
     *
     * Geometry is built on the raster surface's own pixel lattice, so the finished
     * (traced) contour widths are exact multiples of the pixel:
     *  - every post/bar is {@code post} pixels = ceil(minCut / px) (never below the
     *    configured minimum, whatever the rounding);
     *  - every open space is at least {@code hole} pixels in both directions, so no
     *    enclosed hole is narrower than max(1.75 min, 3 gap) and it is never a speck;
     *  - the stitch pitch is the artistic pitch for the size and Detail, but never below
     *    post + hole nor post / YARN_X: small artwork gets a coarser grid instead of
     *    sub-minimum stitches or spaces that are only specks;
     *  - each square owns its left post and top bar, extended by one post width so that
     *    same-colour neighbours overlap by a full post x post joint (never a diagonal
     *    pixel touch), while different-colour neighbours are separated by a whole space;
     *  - squares are only stitched where their entire footprint is inside the Snip, so
     *    the Snip never clips a post into a sliver; the solid L1 foundation shows at the
     *    ragged edge, as the background does around real filet work.
     * Colour sheet and stitch are chosen together: an open space shows L1 through it, so
     * each (yarn colour, stitch) pair is a physical mix with the foundation. Cells take
     * the pair closest to the photograph (rank-mapped lightness, photo chroma) with
     * serpentine error diffusion of lightness, and a small preference for open stitches.
     */
    static final class FiletCrochet {
        private FiletCrochet(){}
        static final int MESH=0,HALF=1,BLOCK=2;
        /** Preference for open stitches (OKLab cost units): keeps the crochet structure
         * visible where the tone allows it. */
        static final double[] STITCH_BIAS={0,.0004,.0010};
        /** Upper bound of yarn (post) width as a fraction of the stitch pitch. Thread that
         * is thin against wide spaces reads as filet mesh; at 0.40-0.50 the open spaces read
         * as dots instead (measured M5, see docs/engineering/M5-CROCHET-SMOOTHING.md). */
        static final float YARN_X=.30f;
        /** Corner fillet leg as a fraction of the shorter space side (softened chain space). */
        static final float FILLET_X=.12f;
        static final class Lattice {
            final int[] xs,ys;final int post,hole;final float mm;
            Lattice(int[] xs,int[] ys,int post,int hole,float mm){this.xs=xs;this.ys=ys;this.post=post;this.hole=hole;this.mm=mm;}
            int cols(){return xs.length-1;}
            int rows(){return ys.length-1;}
            static int postPx(float mm,Options o){return Math.max(1,(int)Math.ceil(o.minCutMm/mm-.02f));}
            static int holePx(float mm,Options o){
                float holeMm=Math.max(1.75f*o.minCutMm,3f*o.gapMm);
                return Math.max(2,(int)Math.ceil(holeMm/mm-.02f));
            }
            /** Physical stitch pitch in mm before pixel quantisation. */
            static float artisticPitchMm(StyleKit.Source s,Options o){
                float p=(6.55f-.0285f*o.detail)*(float)Math.sqrt(s.widthMm/279.4f);
                return o.draft?p*1.22f:p;
            }
            static Lattice build(PatternPortraitTrioEngine.Surface s,Options o){
                float mm=s.mm;int post=postPx(mm,o),hole=holePx(mm,o);
                int wEff=Math.min(s.w,(int)Math.floor(s.src.widthMm/mm+1e-3));
                int hEff=Math.min(s.h,(int)Math.floor(s.src.heightMm/mm+1e-3));
                int pitch=Math.max(Math.max(post+hole,(int)Math.ceil(post/YARN_X)),Math.round(artisticPitchMm(s.src,o)/mm));
                // Bounded work and per-sheet loop count (finish() rejects > 7000 loops).
                int cap=o.draft?4300:6500;
                double cells=((double)(wEff-post)/pitch+1)*((double)(hEff-post)/pitch+1);
                if(pitch>=2*post+2*hole)cap/=2; // half blocks hold two spaces each
                if(cells>cap)pitch=(int)Math.ceil(pitch*Math.sqrt(cells/cap));
                int cols=(wEff-post)/pitch,rows=(hEff-post)/pitch;
                if(cols<1||rows<1)throw new IllegalArgumentException(
                    "Artwork too small for crochet stitches at this minimum cut width; increase size");
                return new Lattice(spread(wEff-post,cols),spread(hEff-post,rows),post,hole,mm);
            }
            /** cols+1 post positions over [0,span]; every spacing >= floor(span/cols). */
            static int[] spread(int span,int n){
                int[] a=new int[n+1];
                for(int i=0;i<=n;i++)a[i]=(int)Math.round(i*(double)span/n);
                return a;
            }
            /** One stitch square centred at (x, y) mm, or null when it does not fit. */
            static Lattice single(PatternPortraitTrioEngine.Surface s,float x,float y,float pitchMm,Options o){
                float mm=s.mm;int post=postPx(mm,o),hole=holePx(mm,o);
                int pitch=Math.max(post+hole,Math.round(pitchMm/mm));
                int x0=Math.round(x/mm-(pitch+post)*.5f),y0=Math.round(y/mm-(pitch+post)*.5f);
                if(x0<0||y0<0||x0+pitch+post>s.w||y0+pitch+post>s.h)return null;
                return new Lattice(new int[]{x0,x0+pitch},new int[]{y0,y0+pitch},post,hole,mm);
            }
        }
        /** Paint pixel columns [x0,x1) x rows [y0,y1). Pixel centres are never on an edge. */
        static void rect(PatternPortraitTrioEngine.Surface s,int layer,int x0,int y0,int x1,int y1){
            if(x1<=x0||y1<=y0)return;
            float m=s.mm;
            s.polygon(layer,new float[]{x0*m,y0*m,x1*m,y0*m,x1*m,y1*m,x0*m,y1*m});
        }
        /** Right-angle fillet of k pixels at a space corner; (sx, sy) points into the space. */
        static void fillet(PatternPortraitTrioEngine.Surface s,int layer,int cx,int cy,int sx,int sy,int k){
            if(k<=0)return;
            float m=s.mm,d=k+.5f; // vertices half a pixel out: no pixel centre on the diagonal
            s.polygon(layer,new float[]{cx*m,cy*m,(cx+sx*d)*m,cy*m,cx*m,(cy+sy*d)*m});
        }
        static int at(int[][] grid,int r,int c){
            return r<0||c<0||r>=grid.length||c>=grid[r].length?-1:grid[r][c];
        }
        /** Window width that admits a centre post with two weedable spaces. */
        static boolean halfFits(Lattice g,int c){
            return g.xs[c+1]-g.xs[c]-g.post>=2*g.hole+g.post;
        }
        /** Draw one stitch square. layerGrid holds -1 where no square is stitched. */
        static void drawCell(PatternPortraitTrioEngine.Surface s,Lattice g,int r,int c,int layer,int state,
                             int[][] layerGrid,int[][] stateGrid){
            int P=g.post,x0=g.xs[c],x1=g.xs[c+1],y0=g.ys[r],y1=g.ys[r+1];
            int right=at(layerGrid,r,c+1),below=at(layerGrid,r+1,c);
            // Own left post and top chain, each extended one post width into the next joint.
            rect(s,layer,x0,y0,x0+P,y1+P);
            rect(s,layer,x0,y0,x1+P,y0+P);
            // Closing post / chain where no neighbouring square supplies it (Snip edge, last row).
            if(right<0)rect(s,layer,x1,y0,x1+P,y1+P);
            if(below<0)rect(s,layer,x0,y1,x1+P,y1+P);
            int wx0=x0+P,wx1=x1,wy0=y0+P,wy1=y1;
            // A block covers the whole extended square, so a same-colour diagonal
            // neighbour overlaps it by a full post x post joint (never a pixel corner).
            if(state==BLOCK){rect(s,layer,x0,y0,x1+P,y1+P);return;}
            // Which framing members of this square are cut from THIS sheet (top bar and
            // left post always are; closing members too).
            boolean rightOwn=right<0||right==layer,belowOwn=below<0||below==layer;
            if(state==HALF&&halfFits(g,c)){
                int m=(wx0+wx1-P)/2;
                rect(s,layer,m,y0,m+P,y1);
                space(s,layer,wx0,wy0,m,wy1,true,true,belowOwn);
                space(s,layer,m+P,wy0,wx1,wy1,true,rightOwn,belowOwn);
                return;
            }
            space(s,layer,wx0,wy0,wx1,wy1,true,rightOwn,belowOwn);
        }
        /** Rounded chain space: fillets soften the corners without narrowing the space
         * below its inscribed width (leg <= FILLET_X of the shorter side). A fillet is
         * drawn only where BOTH members meeting at that corner are on this sheet: a
         * fillet against another sheet's post would leave a free 45-degree spike whose
         * tip is narrower than the cut minimum. The top member is always own yarn. */
        static void space(PatternPortraitTrioEngine.Surface s,int layer,int x0,int y0,int x1,int y1,
                          boolean leftOwn,boolean rightOwn,boolean bottomOwn){
            int k=(int)Math.floor(FILLET_X*Math.min(x1-x0,y1-y0));
            if(k<1)return;
            if(leftOwn)fillet(s,layer,x0,y0,1,1,k);
            if(rightOwn)fillet(s,layer,x1,y0,-1,1,k);
            if(leftOwn&&bottomOwn)fillet(s,layer,x0,y1,1,-1,k);
            if(rightOwn&&bottomOwn)fillet(s,layer,x1,y1,-1,-1,k);
        }
        static double[] lin(int rgb){
            return new double[]{srgbLin((rgb>>16)&255),srgbLin((rgb>>8)&255),srgbLin(rgb&255)};
        }
        static double srgbLin(int c){double v=c/255.0;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
        static int linToRgb(double[] l){
            int r=enc(l[0]),g=enc(l[1]),b=enc(l[2]);return 0xff000000|(r<<16)|(g<<8)|b;
        }
        static int enc(double v){v=Math.max(0,Math.min(1,v));
            double s=v<=.0031308?v*12.92:1.055*Math.pow(v,1/2.4)-.055;return (int)Math.round(s*255);}
        /** Fraction of a square covered by yarn for each stitch, from the real pixel lattice. */
        static double[] coverage(Lattice g){
            int pitch=Math.max(1,(g.xs[g.cols()]-g.xs[0])/g.cols());
            int P=g.post,W=pitch-P;
            double cell=(double)pitch*pitch;
            int k=(int)Math.floor(FILLET_X*W);
            double mesh=1-(W*(double)W-2*k*(k+1))/cell;
            double half=mesh;
            if(W>=2*g.hole+P){int h=(W-P)/2;int kh=(int)Math.floor(FILLET_X*Math.min(h,W));
                half=1-(2.0*h*W-4*kh*(kh+1))/cell;}
            return new double[]{mesh,half,1};
        }

        static StyleKit.Result generate(StyleKit.Source src,Options o){
            PatternPortraitTrioEngine.Surface surface=new PatternPortraitTrioEngine.Surface(src,o.layers,o.minCutMm,o.gapMm,o.draft,false);
            int[] colors=o.palette==null?autoColors(src,o.layers):o.palette.clone();
            surface.fillValid(0); // solid, protected physical L1 foundation
            Lattice g=Lattice.build(surface,o);
            int rows=g.rows(),cols=g.cols();
            // Squares whose whole footprint (including closing post/chain) is inside the Snip.
            int W=surface.w;int[] sum=new int[(W+1)*(surface.h+1)];
            for(int y=0;y<surface.h;y++){
                if((y&63)==0)CancellationCheckpoint.check();
                int run=0;
                for(int x=0;x<W;x++){run+=surface.valid[y*W+x]?1:0;sum[(y+1)*(W+1)+x+1]=sum[y*(W+1)+x+1]+run;}
            }
            int[][] layer=new int[rows][cols],state=new int[rows][cols];
            int subject=0;
            for(int r=0;r<rows;r++)for(int c=0;c<cols;c++){
                int x0=g.xs[c],x1=g.xs[c+1]+g.post,y0=g.ys[r],y1=g.ys[r+1]+g.post;
                int n=sum[y1*(W+1)+x1]-sum[y0*(W+1)+x1]-sum[y1*(W+1)+x0]+sum[y0*(W+1)+x0];
                boolean in=n==(x1-x0)*(y1-y0);
                layer[r][c]=in?0:-1;if(in)subject++;
            }
            if(subject<o.layers-1)throw new IllegalArgumentException(
                "Not enough photo area inside Snip for all requested vinyl layers");
            assign(src,o,g,colors,layer,state);
            int[] counts=new int[o.layers],stitches=new int[3];
            for(int r=0;r<rows;r++){
                if((r&7)==0)CancellationCheckpoint.check();
                for(int c=0;c<cols;c++){
                    if(layer[r][c]<0)continue;
                    drawCell(surface,g,r,c,layer[r][c],state[r][c],layer,state);
                    counts[layer[r][c]]++;stitches[state[r][c]]++;
                }
            }
            StyleKit.Result result=surface.finish(colors,"craft-"+o.pattern+"-layer",subject,o.minCutMm);
            result.paperArgb=o.paperArgb;
            for(int l=0;l<result.layers.size();l++){
                if(result.layers.get(l).loops.isEmpty())
                    throw new IllegalStateException("L"+(l+1)+" has no cuttable geometry; increase size or reduce layers");
            }
            result.summary=NAMES[o.pattern]+" · solid L1 + "+(o.layers-1)+
                " photo-balanced registered cut overlays · "+subject+" motifs · "+o.layers+
                " real physical vinyl layers · overlay motifs "+
                Arrays.toString(Arrays.copyOfRange(counts,1,counts.length))+
                " · filet "+cols+"x"+rows+" squares, "+stitches[MESH]+" open / "+stitches[HALF]+" half / "+
                stitches[BLOCK]+" block · post "+String.format(java.util.Locale.US,"%.2f",g.post*g.mm)+
                " mm, space >= "+String.format(java.util.Locale.US,"%.2f",g.hole*g.mm)+
                " mm · min cut "+o.minCutMm+" mm";
            return result;
        }

        /** Joint yarn-colour + stitch choice per square (see class comment). Every overlay
         * sheet receives at least one square. */
        static void assign(StyleKit.Source src,Options o,Lattice g,int[] colors,int[][] layer,int[][] state){
            int rows=g.rows(),cols=g.cols(),n=o.layers;
            double[] cover=coverage(g);
            boolean halfAny=false;for(int c=0;c<cols;c++)halfAny|=halfFits(g,c);
            double[] base=lin(colors[0]);
            // Effective colour of (sheet i, stitch s) over the foundation, in OKLab.
            double[][][] eff=new double[n][3][];
            double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
            for(int i=1;i<n;i++){
                double[] y=lin(colors[i]);
                for(int s=0;s<3;s++){
                    double f=cover[s];
                    eff[i][s]=PerceptualVinylColor.lab(linToRgb(new double[]{
                        f*y[0]+(1-f)*base[0],f*y[1]+(1-f)*base[1],f*y[2]+(1-f)*base[2]}));
                    if(s==HALF&&!halfAny)continue;
                    lo=Math.min(lo,eff[i][s][0]);hi=Math.max(hi,eff[i][s][0]);
                }
            }
            float pitchMm=(g.xs[cols]-g.xs[0])*g.mm/cols;
            float[][] tone=new float[rows][cols];
            double[][][] lab=new double[rows][cols][];
            ArrayList<float[]> ranked=new ArrayList<>();
            for(int r=0;r<rows;r++){
                if((r&7)==0)CancellationCheckpoint.check();
                for(int c=0;c<cols;c++){
                    if(layer[r][c]<0)continue;
                    float x=(g.xs[c]+g.post+g.xs[c+1])*.5f*g.mm,y=(g.ys[r]+g.post+g.ys[r+1])*.5f*g.mm;
                    tone[r][c]=textileTone(src,x,y,pitchMm);
                    double[] a=new double[3];
                    float q=pitchMm*.25f;
                    for(int k=0;k<5;k++){
                        float sx=x+(k==1?-q:k==2?q:0),sy=y+(k==3?-q:k==4?q:0);
                        double[] p=PerceptualVinylColor.lab(src.colorAt(sx,sy));
                        a[0]+=p[0]*.2;a[1]+=p[1]*.2;a[2]+=p[2]*.2;
                    }
                    lab[r][c]=a;
                    ranked.add(new float[]{tone[r][c],r,c});
                }
            }
            // Lightness target: half photo tone, half rank, mapped onto the reachable range.
            ranked.sort((p,q)->{int cmp=Float.compare(p[0],q[0]);if(cmp!=0)return cmp;
                cmp=Float.compare(p[1],q[1]);return cmp!=0?cmp:Float.compare(p[2],q[2]);});
            double[][] target=new double[rows][cols];
            int m=ranked.size();
            for(int i=0;i<m;i++){
                float[] e=ranked.get(i);
                double rank=m<2?.5:i/(double)(m-1);
                target[(int)e[1]][(int)e[2]]=lo+(hi-lo)*(.5*rank+.5*e[0]);
            }
            double[][] err=new double[rows+1][cols+2];
            for(int r=0;r<rows;r++){
                if((r&7)==0)CancellationCheckpoint.check();
                boolean ltr=(r&1)==0;
                for(int k=0;k<cols;k++){
                    int c=ltr?k:cols-1-k;
                    if(layer[r][c]<0)continue;
                    double want=target[r][c]+err[r][c+1];
                    double[] p=lab[r][c];
                    double best=Double.POSITIVE_INFINITY;int bi=1,bs=BLOCK;
                    boolean half=halfFits(g,c);
                    for(int i=1;i<n;i++)for(int s=0;s<3;s++){
                        if(s==HALF&&!half)continue;
                        double[] e=eff[i][s];
                        double dl=want-e[0],da=p[1]-e[1],db=p[2]-e[2];
                        double cost=1.95*dl*dl+1.03*(da*da+db*db)+STITCH_BIAS[s];
                        if(cost<best){best=cost;bi=i;bs=s;}
                    }
                    layer[r][c]=bi;state[r][c]=bs;
                    double q=Math.max(-.25,Math.min(.25,want-eff[bi][bs][0]));
                    int f=ltr?1:-1;
                    err[r][c+1+f]+=q*7/16;
                    err[r+1][c+1-f]+=q*3/16;
                    err[r+1][c+1]+=q*5/16;
                    err[r+1][c+1+f]+=q/16;
                }
            }
            // Every physical sheet must carry yarn: give an unused colour the squares that
            // suit it best, taken from sheets that keep at least two squares.
            int[] count=new int[n];
            for(int[] row:layer)for(int v:row)if(v>0)count[v]++;
            for(int i=1;i<n;i++){
                if(count[i]>0)continue;
                double best=Double.POSITIVE_INFINITY;int br=-1,bc=-1,bs=BLOCK;
                for(int r=0;r<rows;r++)for(int c=0;c<cols;c++){
                    int cur=layer[r][c];if(cur<=0||count[cur]<2)continue;
                    double[] p=lab[r][c];
                    for(int s=0;s<3;s++){
                        if(s==HALF&&!halfFits(g,c))continue;
                        double[] e=eff[i][s];
                        double dl=target[r][c]-e[0],da=p[1]-e[1],db=p[2]-e[2];
                        double cost=1.95*dl*dl+1.03*(da*da+db*db);
                        if(cost<best){best=cost;br=r;bc=c;bs=s;}
                    }
                }
                if(br<0)throw new IllegalArgumentException("Not enough photo area inside Snip for all requested vinyl layers");
                count[layer[br][bc]]--;layer[br][bc]=i;state[br][bc]=bs;count[i]++;
            }
        }
    }
    /** Quadratic Bezier approximated with constant-width contiguous cuttable ribbon segments. */
    private static void curved(PatternPortraitTrioEngine.Surface s,int layer,float x0,float y0,
        float cx,float cy,float x1,float y1,float thickness,int steps){
        float px=x0,py=y0;
        for(int i=1;i<=steps;i++){
            float t=i/(float)steps,u=1-t;
            float nx=u*u*x0+2*u*t*cx+t*t*x1;
            float ny=u*u*y0+2*u*t*cy+t*t*y1;
            s.segment(layer,px,py,nx,ny,thickness);
            px=nx;py=ny;
        }
    }
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Irregular, physically layered confetti. The sampling coordinate system is
 * continuous millimetres, not an underlying per-piece lattice.
 *
 * Primary pieces are relaxed random placements with an accelerated near-neighbour
 * test. Secondary pieces collect near primary pieces like a natural tossed pile.
 * Cross-sheet overlap is intentional; per-sheet overlaps are raster-unioned before
 * SVG contour extraction by PatternPortraitTrioEngine.Surface.
 */
final class FallenConfettiEngine {
    private FallenConfettiEngine() {}
    /** API-26 safe, 24-bit uniform fraction. SplittableRandom.nextFloat() is
     * inherited from RandomGenerator on API 35 and must not be called on 26-34.
     * nextInt() is a concrete SplittableRandom method available since API 24.
     * Top 24 bits also keep the result strictly inside [0, 1).
     */
    static float unitFloat(SplittableRandom rnd) {
        return (rnd.nextInt() >>> 8) * 0x1.0p-24f;
    }
    static boolean coinFlip(SplittableRandom rnd) {
        return rnd.nextInt() < 0;
    }
    static final int DISC=0, PAPER=1, STRIP=2, SQUARE=3;
    static final int MODE_MIXED=0, MODE_ROUND=1, MODE_SQUARE=2,
                     MODE_PAPER=3, MODE_STREAMER=4, MODE_FALLEN=5;
    static final class Piece {
        final float x,y,radius,angle,stretch;
        final int kind;
        int slot;
        final long seed;
        final boolean clustered;
        float exclusion; // sampling separation, not physical geometry
        Piece(float x,float y,float radius,float angle,float stretch,
              int kind,int slot,long seed,boolean clustered){
            this.x=x;this.y=y;this.radius=radius;this.angle=angle;
            this.stretch=stretch;this.kind=kind;this.slot=slot;this.seed=seed;
            this.clustered=clustered;
        }
    }
    static final class Plan {
        /** M8 sheet clearance: overlay pieces moved to a full weed gap / not placed. */
        int nudged,dropped;
        final ArrayList<Piece> pieces=new ArrayList<>();
        final int[] types=new int[4];
        int clustered;
        int primary;
        float pitch;
        int overlaps;
    }
    private static final class TonalSlots {
        final int[] lightnessOrder;
        final float[] bounds;
        final PhotoSwatchAssociation association;
        TonalSlots(StyleKit.Source src,int[] palette){
            association=new PhotoSwatchAssociation(Arrays.copyOfRange(palette,1,palette.length),
                palette.length-1,null,src,PhotoSwatchAssociation.ORNAMENTS);
            final int n=palette.length-1;
            Integer[] sorted=new Integer[n];
            for(int i=0;i<n;i++)sorted[i]=i+1;
            Arrays.sort(sorted,(a,b)->Double.compare(PerceptualVinylColor.lab(palette[a])[0],PerceptualVinylColor.lab(palette[b])[0]));
            lightnessOrder=new int[n];
            for(int i=0;i<n;i++)lightnessOrder[i]=sorted[i];
            // The actual palette is only read; physical order is not changed.
            bounds=new float[Math.max(0,n-1)];
            // Bounded histogram of a deterministic Snip-aware sample, not a
            // full-photo floating point sort on every palette change.
            int[] hist=new int[256];int count=0,validSeen=0;
            int stride=Math.max(1,src.valid.length/24000);
            for(int i=0;i<src.valid.length;i++){
                if((i&8191)==0)CancellationCheckpoint.check();
                if(!src.valid[i]||validSeen++%stride!=0)continue;
                int bucket=Math.max(0,Math.min(255,(int)Math.round(255*PerceptualLayerColorEngine.light(src.argb[i]))));
                hist[bucket]++;count++;
            }
            for(int i=0;i<bounds.length;i++){
                int target=(int)(((long)(i+1)*count)/n),sum=0,j=0;
                while(j<255&&(sum+=hist[j])<=target)j++;
                bounds[i]=count==0?(i+1f)/n:j/255f;
            }
        }
        int pick(StyleKit.Source src,float x,float y,SplittableRandom rnd){
            float lum=(float)PerceptualVinylColor.lab(src.colorAt(x,y))[0];
            int rank=0;
            while(rank<bounds.length&&lum>bounds[rank])rank++;
            if(lightnessOrder.length==1)return lightnessOrder[0];
            // Keep source-driven tonal rank while selecting the physically closest
            // color among its adjacent ranks. Random color overrides are removed:
            // they previously put unrelated swatches into facial highlight cells.
            return 1+association.chooseNearRank(src.colorAt(x,y),rank,1);
        }
    }
    static Plan sample(StyleKit.Source src,PatternPortraitTrioEngine.Options o,int[] colors){
        // Low-detail remains deliberately coarse; transition continuously above 25.
        return sample(src,o,colors,Math.max(0f,(o.detail-25)/75f));
    }
    static Plan sample(StyleKit.Source src,PatternPortraitTrioEngine.Options o,int[] colors,float adaptive){
        final Plan plan=new Plan();
        final SplittableRandom rnd=new SplittableRandom(o.seed^0x4A19F5C2E75B3D10L);
        final TonalSlots tone=new TonalSlots(src,colors);
        final ConfettiCoverageModel coverage=adaptive>0?new ConfettiCoverageModel(colors):null;
        final float width=src.widthMm, height=src.heightMm;
        // At 11 in, a typical chip is 2-6 mm wide; three size classes cover
        // larger flakes, medium paper chips and fine highlight fragments.
        float pitch=Math.max(Math.max(o.minCutMm*6.8f,o.spacingMm*3.7f),
                             7.4f-3.55f*o.detail/100f);
        // Fallen mode uses less uniform anchor separation and more clustered
        // debris; other styles share the same continuous scatter plane.
        if(adaptive>0){
            float fine=Math.max(Math.max(o.minCutMm*4.6f+o.gapMm*.4f,o.spacingMm*3.7f),
                                6.4f-3.7f*o.detail/100f);
            pitch+=(fine-pitch)*adaptive;
        }
        final boolean fallen=o.confettiMode==MODE_FALLEN;
        plan.pitch=pitch;
        float spacing=pitch*(fallen?.55f:.62f);
        float gridCell=spacing*.707107f*(adaptive>0?.72f:1f);
        int gw=Math.max(1,(int)Math.ceil(width/gridCell));
        int gh=Math.max(1,(int)Math.ceil(height/gridCell));
        // Bound the grid for huge physical sizes / tiny cuts.
        if((long)gw*gh>1_500_000L)throw new IllegalArgumentException("Confetti scatter grid too large");
        int[] grid=new int[gw*gh];Arrays.fill(grid,-1);
        final ArrayList<Piece> anchors=new ArrayList<>();
        int target=Math.min(3800+(int)(adaptive*800),Math.max(28,(int)(width*height/(pitch*pitch)*(.72f+.23f*adaptive))));
        int attempts=Math.min(140000,target*24);
        for(int i=0;i<attempts&&anchors.size()<target;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if((i&511)==0)StyleKit.checkCancelled();
            float x=unitFloat(rnd)*width, y=unitFloat(rnd)*height;
            if(!src.validAt(x,y))continue;
            float edge=adaptive>0?subjectEdge(src,x,y,pitch*.32f):edge(src,x,y,pitch*.20f);
            float light=src.lumAt(x,y);
            // Scattering is intentionally incomplete: broad islands of the
            // underlay remain exposed. Face detail earns extra fragments.
            float probability=(fallen?.58f:.66f)+.20f*edge+.08f*light;
            float localSpacing=spacing;
            if(coverage!=null){
                coverage.choose(src.colorAt(x,y));
                float demand=coverage.fraction;
                probability=probability*(1-adaptive)+adaptive*(.16f+.70f*demand+.13f*edge);
                localSpacing=spacing*(1+adaptive*(.37f-.62f*demand-.22f*edge));
                localSpacing=Math.max(spacing*.72f,localSpacing);
            }
            if(unitFloat(rnd)>probability)continue;
            int cx=Math.min(gw-1,(int)(x/gridCell));
            int cy=Math.min(gh-1,(int)(y/gridCell));
            boolean free=true;
            int reach=adaptive>0?4:2;
            for(int yy=Math.max(0,cy-reach);yy<=Math.min(gh-1,cy+reach)&&free;yy++)
                for(int xx=Math.max(0,cx-reach);xx<=Math.min(gw-1,cx+reach);xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
                    int j=grid[yy*gw+xx];
                    if(j<0)continue;
                    Piece q=anchors.get(j);
                    float dx=x-q.x,dy=y-q.y;
                    float separation=adaptive>0?(localSpacing+q.exclusion)*.5f:spacing;
                    if(dx*dx+dy*dy<separation*separation){free=false;break;}
                }
            if(!free)continue;
            float localPitch=pitch;
            if(coverage!=null)localPitch*=1+adaptive*(.26f*coverage.fraction-.29f*edge);
            Piece main=piece(src,o,tone,rnd,x,y,localPitch,false,-1);
            if(main==null)continue;
            if(coverage!=null&&unitFloat(rnd)<adaptive)main.slot=coverage.slot;
            main.exclusion=localSpacing;
            grid[cy*gw+cx]=anchors.size();
            anchors.add(main);
            plan.pieces.add(main);plan.types[main.kind]++;
        }
        plan.primary=anchors.size();
        if(anchors.isEmpty())return plan;
        // Cluster offspring do NOT obey the minimum spacing: some fall over
        // neighbours, which is the difference between natural confetti and stipple.
        int satellites=(int)(anchors.size()*(fallen?.48f:.20f)+(fallen?.36f:.20f)*anchors.size()*o.organic/100f);
        for(int i=0;i<satellites;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if((i&511)==0)StyleKit.checkCancelled();
            Piece parent=anchors.get(rnd.nextInt(anchors.size()));
            float angle=unitFloat(rnd)*(float)(Math.PI*2);
            float distance=parent.radius*(.30f+unitFloat(rnd)*(fallen?1.55f:1.95f));
            float x=parent.x+(float)Math.cos(angle)*distance;
            float y=parent.y+(float)Math.sin(angle)*distance*(fallen?.68f:1f);
            if(fallen){
                // A slight downward drift creates localized heaps without a
                // repeating row or moving pieces beyond the Snip boundary.
                y+=parent.radius*(.10f+.28f*unitFloat(rnd));
            }
            if(!src.validAt(x,y))continue;
            Piece child=piece(src,o,tone,rnd,x,y,pitch*.72f,true,parent.slot);
            if(child==null)continue;
            if(coverage!=null){
                coverage.choose(src.colorAt(x,y));
                if(unitFloat(rnd)<adaptive)child.slot=coverage.slot;
            }
            plan.pieces.add(child);plan.types[child.kind]++;
            plan.clustered++;
            if(distance<parent.radius+child.radius)plan.overlaps++;
        }
        ensureUsefulCoverage(plan,src,colors);
        return plan;
    }
    /** Reserve the minimum useful population on physically requested sheets,
     * including two identical vinyl rolls. Unlike random neighboring-slot
     * assignment, only the least-cost interior primary motifs are moved. */
    private static void ensureUsefulCoverage(Plan plan,StyleKit.Source src,int[] colors){
        int[] count=new int[colors.length];
        for(Piece p:plan.pieces)count[p.slot]++;
        int floor=Math.min(4,plan.pieces.size()/Math.max(1,colors.length-1));
        for(int slot=1;slot<colors.length;slot++)while(count[slot]<floor){
            CancellationCheckpoint.check();
            Piece best=null;double bestCost=Double.POSITIVE_INFINITY;
            for(int i=0;i<plan.pieces.size();i++){
                if((i&1023)==0)CancellationCheckpoint.check();
                Piece p=plan.pieces.get(i);
                if(count[p.slot]<=floor||p.slot==slot)continue;
                int rgb=src.colorAt(p.x,p.y);
                double extra=PerceptualLayerColorEngine.error(rgb,colors[slot])-
                    PerceptualLayerColorEngine.error(rgb,colors[p.slot]);
                // Prefer a robust interior primary cut rather than a clipped chip.
                float r=p.radius*.5f;
                boolean interior=src.validAt(p.x-r,p.y)&&src.validAt(p.x+r,p.y)&&
                    src.validAt(p.x,p.y-r)&&src.validAt(p.x,p.y+r);
                double cost=extra+(interior?0:1)+(p.clustered?.01:0)-.001*Math.min(4,p.radius);
                if(cost<bestCost){best=p;bestCost=cost;}
            }
            if(best==null)break;
            count[best.slot]--;best.slot=slot;count[slot]++;
        }
    }
    private static Piece piece(StyleKit.Source src,PatternPortraitTrioEngine.Options o,
                               TonalSlots tone,SplittableRandom rnd,float x,float y,float pitch,
                               boolean clustered,int parentSlot){
        int slot=tone.pick(src,x,y,rnd);
        // When two scattered pieces meet, use a different actual swatch where
        // practical; physical order determines which piece covers the other.
        if(clustered&&o.layers>2)coinFlip(rnd); // Palette-independent sampling; never override a chosen color.
        float u=unitFloat(rnd);
        float radius=pitch*(.245f+.255f*unitFloat(rnd));
        if(u<.17f)radius*=1.48f;
        else if(u>.83f)radius*=.60f;
        radius=Math.max(o.minCutMm*1.48f,radius);
        int style=rnd.nextInt(100), kind;
        switch(o.confettiMode){
            case MODE_ROUND:kind=DISC;break;
            case MODE_SQUARE:kind=SQUARE;break;
            case MODE_PAPER:kind=PAPER;break;
            case MODE_STREAMER:kind=STRIP;break;
            case MODE_FALLEN:kind=style<18?DISC:style<37?SQUARE:style<82?PAPER:STRIP;break;
            default:kind=style<26?DISC:style<45?SQUARE:style<78?PAPER:STRIP;
        }
        float stretch=.72f+unitFloat(rnd)*.48f;
        if(kind==STRIP)stretch=1.24f+unitFloat(rnd)*.58f;
        if(kind==SQUARE)stretch=.84f+unitFloat(rnd)*.32f;
        float angle=unitFloat(rnd)*(float)(2*Math.PI);
        return new Piece(x,y,radius,angle,stretch,kind,slot,rnd.nextLong(),clustered);
    }
    /**
     * Photo-derived physical palette, darkest (L1 foundation) to lightest.
     *
     * Build 555: slots are spaced evenly in perceptual (OKLab) lightness across the
     * photo's own 4.5..95.5 % range, not at equal sample-count percentiles. With
     * equal-count percentiles a side-lit or bimodal photo put the first overlay
     * inside the same dark cluster as the foundation (dE ~ 11), so rings in shadow
     * were nearly invisible and each sheet covered only part of the artwork. When the
     * photo has no samples near a target lightness (a tonal gap), the nearest real
     * color keeps its hue and is shifted to the target lightness.
     */
    static int[] autoPalette(StyleKit.Source source,int count){
        int n=0;
        for(boolean b:source.valid)if(b)n++;
        int stride=Math.max(1,n/4096);
        long[] samples=new long[(n+stride-1)/stride];
        int seen=0,k=0;
        for(int i=0;i<source.valid.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if(!source.valid[i])continue;
            if((seen++%stride)!=0)continue;
            // Sort by source luminance using a fixed-point key, preserve color.
            long l=(long)(PerceptualLayerColorEngine.light(source.argb[i])*0xFFFFFFL);
            samples[k++]=(l<<32)|(source.argb[i]&0xFFFFFFFFL);
        }
        Arrays.sort(samples,0,k);
        int[] colors=new int[count];
        if(k==0){Arrays.fill(colors,0xFF808080);return colors;}
        double lo=keyLight(samples[Math.min(k-1,(int)(.045*(k-1)))]),hi=keyLight(samples[Math.min(k-1,(int)(.955*(k-1)))]);
        // A dim or flat photo can span only a few lightness units; vinyl tones that close
        // are indistinguishable once cut. Guarantee a usable span around the photo's center.
        double span=minLightSpan(count);
        if(count>1&&hi-lo<span){
            double mid=(lo+hi)/2;
            lo=Math.max(.18,mid-span/2);hi=Math.min(.97,lo+span);lo=Math.max(.18,hi-span);
        }
        int window=Math.max(1,k/(Math.max(3,count*5)));
        for(int slot=0;slot<count;slot++){if(((slot)&63)==0)CancellationCheckpoint.check();
            double target=count==1?(lo+hi)/2:lo+(hi-lo)*slot/(count-1.0);
            int center=nearestLight(samples,k,target);
            long rr=0,gg=0,bb=0;int used=0;
            for(int j=Math.max(0,center-window);j<=Math.min(k-1,center+window);j++){
                int c=(int)samples[j];
                rr+=(c>>16)&255;gg+=(c>>8)&255;bb+=c&255;used++;
            }
            int color=0xFF000000|((int)(rr/used)<<16)|((int)(gg/used)<<8)|(int)(bb/used);
            if(Math.abs(PerceptualLayerColorEngine.light(color)-target)>.03)color=shiftToLight(color,target);
            colors[slot]=color;
        }
        return colors;
    }
    /** More physical colors need a wider lightness range to stay distinguishable. */
    static double minLightSpan(int count){return Math.min(.70,.30+.04*count);}
    private static double keyLight(long sample){return (sample>>>32)/(double)0xFFFFFFL;}
    private static int nearestLight(long[] samples,int k,double target){
        long key=((long)(Math.max(0,Math.min(1,target))*0xFFFFFFL))<<32;
        int at=Arrays.binarySearch(samples,0,k,key);
        if(at<0)at=-at-1;
        if(at>=k)return k-1;
        if(at>0&&Math.abs(keyLight(samples[at-1])-target)<Math.abs(keyLight(samples[at])-target))return at-1;
        return at;
    }
    /** Keep hue, move OKLab lightness to target by blending toward white or black (bisection). */
    static int shiftToLight(int argb,double target){
        double base=PerceptualLayerColorEngine.light(argb);
        int toward=target>base?0xFFFFFFFF:0xFF000000;
        double a=0,b=1;int best=argb;
        for(int it=0;it<18;it++){
            double t=(a+b)/2;int c=mix(argb,toward,t);
            double l=PerceptualLayerColorEngine.light(c);best=c;
            if((target>base)==(l<target))a=t;else b=t;
        }
        return best;
    }
    private static int mix(int c,int d,double t){
        int r=(int)Math.round(((c>>16)&255)*(1-t)+((d>>16)&255)*t);
        int g=(int)Math.round(((c>>8)&255)*(1-t)+((d>>8)&255)*t);
        int b=(int)Math.round((c&255)*(1-t)+(d&255)*t);
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }
    static int render(PatternPortraitTrioEngine.Surface surface,PatternPortraitTrioEngine.Options o,int[] palette){
        // Mandatory solid Snip silhouette; L1 counts in 2..8.
        return renderPlan(surface,o,sample(surface.src,o,palette));
    }
    static int renderPlan(PatternPortraitTrioEngine.Surface surface,PatternPortraitTrioEngine.Options o,Plan plan){
        surface.fillValid(0);
        SheetClearance clearance=new SheetClearance(o.gapMm+CLEARANCE_MARGIN_MM);
        int placed=0;
        for(int i=0;i<plan.pieces.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if((i&255)==0)StyleKit.checkCancelled();
            Piece piece=plan.pieces.get(i);
            float[] poly=polygon(piece);
            if(surface.vectorSlot(piece.slot)){
                if(piece.kind==DISC)poly=VectorBooleanEngine.ellipse(piece.x,piece.y,piece.radius*piece.stretch,piece.radius/piece.stretch,0,surface.vectorTolerance());
                else if(piece.kind==STRIP)poly=VectorBooleanEngine.roundedStrip(poly,surface.vectorTolerance());
            }
            // M8: the solid silhouette (slot 0) is never moved; overlay pieces must overlap or clear.
            if(piece.slot>0){
                poly=clearance.place(piece.slot,poly,piece.radius*.5f);
                if(poly==null)continue;
            }
            // Different layers may cover one another. On a single sheet Surface
            // unions intersecting pixels, avoiding duplicated cutter boundaries.
            surface.polygon(piece.slot,poly);placed++;
        }
        plan.nudged=clearance.nudged;plan.dropped=clearance.dropped;
        return placed;
    }
    /** Margin over the weed gap so curve flattening and union snapping cannot reopen a sub-gap channel. */
    static final float CLEARANCE_MARGIN_MM=.06f;
    /**
     * M8: manufacturable placement on each overlay sheet. Two pieces on the same sheet either
     * overlap (the union merges them into one piece) or are at least one weed gap apart. A near
     * miss (0 < distance < gap) left a sub-gap waste sliver that no cutter or weeder handles
     * reliably; at 8-11 in these produced thousands of cut findings that the bounded post-union
     * repair could not resolve. A near-miss piece is pushed directly away from its closest
     * neighbour by the shortfall (at most three times); if that would move it more than half its
     * size, it is not placed. Deterministic: depends only on the plan order and the geometry.
     */
    static final class SheetClearance {
        final float clear;static final float CELL=4f;
        final java.util.HashMap<Long,int[]> cells=new java.util.HashMap<>();
        final ArrayList<float[]> shapes=new ArrayList<>(),boxes=new ArrayList<>();
        int[] stamp=new int[256];int query;
        int nudged,dropped;
        SheetClearance(float clear){this.clear=clear;}
        float[] place(int slot,float[] poly,float maxMove){
            float[] cand=poly;float moved=0;
            for(int attempt=0;attempt<3;attempt++){
                float[] miss=nearMiss(slot,cand);
                if(miss==null){
                    if(cand!=poly)nudged++;
                    add(slot,cand);return cand;
                }
                float move=clear-miss[0]+.01f;
                moved+=move;
                if(moved>maxMove)break;
                cand=translate(cand,miss[1]*move,miss[2]*move);
            }
            dropped++;return null;
        }
        private static long key(int slot,int x,int y){return ((long)slot<<48)^((long)(x&0xffffff)<<24)^(y&0xffffff);}
        private void add(int slot,float[] p){
            int id=shapes.size();float[] b=bounds(p);shapes.add(p);boxes.add(b);
            if(id>=stamp.length)stamp=Arrays.copyOf(stamp,stamp.length*2);
            for(int y=(int)Math.floor(b[1]/CELL);y<=(int)Math.floor(b[3]/CELL);y++)
                for(int x=(int)Math.floor(b[0]/CELL);x<=(int)Math.floor(b[2]/CELL);x++){
                    long k=key(slot,x,y);int[] list=cells.get(k);
                    if(list==null){list=new int[]{0,0,0,0,0};cells.put(k,list);}
                    if(list[0]+1>=list.length){list=Arrays.copyOf(list,list.length*2);cells.put(k,list);}
                    list[++list[0]]=id;
                }
        }
        /** Closest same-sheet near miss as {distance, unit push x, unit push y}, or null. */
        private float[] nearMiss(int slot,float[] p){
            float[] b=bounds(p);float[] best=null;query++;
            for(int y=(int)Math.floor((b[1]-clear)/CELL);y<=(int)Math.floor((b[3]+clear)/CELL);y++)
                for(int x=(int)Math.floor((b[0]-clear)/CELL);x<=(int)Math.floor((b[2]+clear)/CELL);x++){
                    int[] list=cells.get(key(slot,x,y));if(list==null)continue;
                    for(int e=1;e<=list[0];e++){
                        int id=list[e];if(stamp[id]==query)continue;stamp[id]=query;
                        float[] qb=boxes.get(id);
                        if(qb[0]>b[2]+clear||qb[2]<b[0]-clear||qb[1]>b[3]+clear||qb[3]<b[1]-clear)continue;
                        float[] q=shapes.get(id);
                        if(overlap(p,q))continue;
                        float[] d=closest(p,q);
                        if(d[0]<clear&&(best==null||d[0]<best[0]))best=d;
                    }
                }
            return best;
        }
    }
    static float[] translate(float[] p,float dx,float dy){float[] q=p.clone();for(int k=0;k<q.length;k+=2){q[k]+=dx;q[k+1]+=dy;}return q;}
    static float[] bounds(float[] p){float a=Float.MAX_VALUE,b=a,c=-a,d=-a;for(int k=0;k<p.length;k+=2){a=Math.min(a,p[k]);c=Math.max(c,p[k]);b=Math.min(b,p[k+1]);d=Math.max(d,p[k+1]);}return new float[]{a,b,c,d};}
    /** Boundaries cross/touch, or one shape contains the other. */
    static boolean overlap(float[] a,float[] b){
        int n=a.length/2,m=b.length/2;
        for(int i=0;i<n;i++){int i2=(i+1)%n;for(int j=0;j<m;j++){int j2=(j+1)%m;
            if(segmentsMeet(a[2*i],a[2*i+1],a[2*i2],a[2*i2+1],b[2*j],b[2*j+1],b[2*j2],b[2*j2+1]))return true;}}
        return inside(a,b[0],b[1])||inside(b,a[0],a[1]);
    }
    private static boolean segmentsMeet(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
        double d1=(bx-ax)*(double)(cy-ay)-(by-ay)*(double)(cx-ax),d2=(bx-ax)*(double)(dy-ay)-(by-ay)*(double)(dx-ax);
        double d3=(dx-cx)*(double)(ay-cy)-(dy-cy)*(double)(ax-cx),d4=(dx-cx)*(double)(by-cy)-(dy-cy)*(double)(bx-cx);
        return d1*d2<=0&&d3*d4<=0;
    }
    private static boolean inside(float[] p,float x,float y){boolean in=false;int n=p.length/2;for(int i=0,j=n-1;i<n;j=i++){float yi=p[2*i+1],yj=p[2*j+1];if((yi>y)!=(yj>y)&&x<p[2*i]+(y-yi)*(p[2*j]-p[2*i])/(yj-yi))in=!in;}return in;}
    /** {distance, ux, uy} of the closest boundary pair; u points from q toward p. */
    static float[] closest(float[] p,float[] q){
        float best=Float.MAX_VALUE,ux=0,uy=0;
        for(int pass=0;pass<2;pass++){float[] a=pass==0?p:q,b=pass==0?q:p;int n=a.length/2,m=b.length/2;
            for(int i=0;i<n;i++){float px=a[2*i],py=a[2*i+1];
                for(int j=0;j<m;j++){int j2=(j+1)%m;float x0=b[2*j],y0=b[2*j+1],x1=b[2*j2],y1=b[2*j2+1];
                    float dx=x1-x0,dy=y1-y0,l=dx*dx+dy*dy;float t=l==0?0:Math.max(0,Math.min(1,((px-x0)*dx+(py-y0)*dy)/l));
                    float ex=px-(x0+t*dx),ey=py-(y0+t*dy),d=(float)Math.sqrt(ex*ex+ey*ey);
                    if(d<best&&d>0){best=d;float sgn=pass==0?1:-1;ux=sgn*ex/d;uy=sgn*ey/d;}}}}
        return new float[]{best,ux,uy};
    }
    static float[] polygon(Piece p){
        SplittableRandom rnd=new SplittableRandom(p.seed);
        if(p.kind==SQUARE){
            float r=p.radius*p.stretch*.90f;
            return rotate(new float[]{-r,-r,r,-r,r,r,-r,r},p.x,p.y,p.angle);
        }
        if(p.kind==STRIP){
            // Rotated slightly torn rectangle, long axis length ≈ 2-4 mm.
            float r=p.radius, halfX=r*p.stretch, halfY=r*(.42f+.23f*unitFloat(rnd));
            float shear=(unitFloat(rnd)-.5f)*r*.32f;
            float[] xy={-halfX,-halfY, halfX,-halfY+shear,
                         halfX*.94f,halfY, -halfX*.91f,halfY-shear*.5f};
            return rotate(xy,p.x,p.y,p.angle);
        }
        int count=p.kind==DISC?12:5+rnd.nextInt(4);
        float[] xy=new float[count*2];
        float phase=p.angle;
        float stretch=p.stretch;
        for(int i=0;i<count;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            double a=phase+i*2*Math.PI/count;
            float vary=p.kind==DISC?.955f+unitFloat(rnd)*.09f:
                                    .68f+unitFloat(rnd)*.55f;
            float r=p.radius*vary;
            xy[2*i]=p.x+(float)Math.cos(a)*r*stretch;
            xy[2*i+1]=p.y+(float)Math.sin(a)*r/stretch;
        }
        return xy;
    }
    private static float[] rotate(float[] xy,float x,float y,float a){
        float c=(float)Math.cos(a),s=(float)Math.sin(a);
        for(int i=0;i<xy.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();
            float dx=xy[i],dy=xy[i+1];
            xy[i]=x+dx*c-dy*s;xy[i+1]=y+dx*s+dy*c;
        }
        return xy;
    }
    private static float subjectEdge(StyleKit.Source s,float x,float y,float r){
        float center=s.lumAt(x,y);
        float a=s.validAt(x+r,y)?s.lumAt(x+r,y):center;
        float b=s.validAt(x-r,y)?s.lumAt(x-r,y):center;
        float c=s.validAt(x,y+r)?s.lumAt(x,y+r):center;
        float d=s.validAt(x,y-r)?s.lumAt(x,y-r):center;
        return StyleKit.clampF((float)Math.hypot(a-b,c-d)*2.5f,0f,1f);
    }
    private static float edge(StyleKit.Source s,float x,float y,float r){
        float dx=s.lumAt(x+r,y)-s.lumAt(x-r,y);
        float dy=s.lumAt(x,y+r)-s.lumAt(x,y-r);
        return StyleKit.clampF((float)Math.hypot(dx,dy)*2.5f,0f,1f);
    }
}

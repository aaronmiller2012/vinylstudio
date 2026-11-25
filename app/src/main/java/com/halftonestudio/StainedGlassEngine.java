package com.halftonestudio;

import java.util.*;

/**
 * Stained glass: edge-aware Voronoi pieces, each filled with one glass colour, separated by lead came.
 * Lead is produced as a CUT-OUT frame (rectangle with every glass piece punched out) so the glass
 * vinyl sits inside it, or as a solid UNDERLAY the glass pieces are laid over.
 */
public final class StainedGlassEngine {
    private StainedGlassEngine() {}

    public enum LeadMode { CUTOUT, UNDERLAY, NONE }

    public static final class Options {
        public float widthMm = 152.4f;
        public int glassColors = 7;           // 2..14
        public int[] palette = null;          // optional fixed glass vinyl colours
        public float minSpacingMm = 4f;
        /** Legacy compatibility: reserved; maximum spacing is not an active control. */
        @Deprecated public float maxSpacingMm = 14f;
        public float edgeBias = 0.8f;
        public int maxPieces = 1200;
        /** Legacy compatibility: fixed SLIC passes; not a user-selectable iteration count. */
        @Deprecated public int relaxIterations = 1;
        public float leadMm = 1.4f;           // width of the came between pieces
        /** Legacy compatibility: no independently generated outer frame. */
        @Deprecated public float frameMm = 3.0f;
        public LeadMode leadMode = LeadMode.CUTOUT;
        public int leadArgb = 0xff1d1d22;
        public float saturationBoost = 0.25f; // glass looks richer than the photo
        public float minPieceAreaMm2 = 3f;
        public float gamma = 1f, contrast = 1f;
        public long seed = 7;
        public int paperArgb = 0xffffffff;
        public boolean[] allowed = null;
        public Options copy() { Options o = new Options(); o.widthMm = widthMm; o.glassColors = glassColors; o.palette = palette; o.minSpacingMm = minSpacingMm; o.maxSpacingMm = maxSpacingMm; o.edgeBias = edgeBias; o.maxPieces = maxPieces; o.relaxIterations = relaxIterations; o.leadMm = leadMm; o.frameMm = frameMm; o.leadMode = leadMode; o.leadArgb = leadArgb; o.saturationBoost = saturationBoost; o.minPieceAreaMm2 = minPieceAreaMm2; o.gamma = gamma; o.contrast = contrast; o.seed = seed; o.paperArgb = paperArgb; o.allowed = allowed; return o; }
        void validate() {
            if (glassColors < 2 || glassColors > 14) throw new IllegalArgumentException("glassColors must be 2..14");
            if (minSpacingMm < 1.5f || maxSpacingMm < minSpacingMm) throw new IllegalArgumentException("Invalid spacing");
            if (leadMm < 0.3f || leadMm > 6) throw new IllegalArgumentException("leadMm must be 0.3..6");
            if (frameMm < 0 || frameMm > 20) throw new IllegalArgumentException("frameMm must be 0..20");
            if (relaxIterations < 0 || relaxIterations > 3) throw new IllegalArgumentException("relaxIterations must be 0..3");
            if (maxPieces < 8 || maxPieces > 4000) throw new IllegalArgumentException("maxPieces must be 8..4000");
        }
    }

    /** Working resolution is independent of camera resolution; cap memory and keep a
     * predictable number of physical panes on phone hardware. Coordinates in this
     * working bitmap map linearly to the original source's physical artboard. */
    static final class Pixels {
        final int w,h; final float px, yScale;
        final boolean[] valid;
        final int[] rgb;
        final float[] l,a,b, edge;
        int validCount;
        Pixels(StyleKit.Source src) {
            float factor=Math.max(1f,Math.max(src.w,src.h)/320f);
            w=Math.max(2,Math.round(src.w/factor));h=Math.max(2,Math.round(src.h/factor));
            px=src.widthMm/w; yScale=src.heightMm/(h*px);
            int n=w*h;valid=new boolean[n];rgb=new int[n];
            l=new float[n];a=new float[n];b=new float[n];edge=new float[n];
            for(int y=0;y<h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
                if((y&31)==0)StyleKit.checkCancelled();
                for(int x=0;x<w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                    int sx=Math.min(src.w-1,(int)((x+.5)*src.w/w));
                    int sy=Math.min(src.h-1,(int)((y+.5)*src.h/h));
                    int si=sy*src.w+sx,i=y*w+x;
                    valid[i]=src.valid[si];if(valid[i])validCount++;
                    rgb[i]=src.argb[si];float[] lab=StyleKit.toLab(rgb[i]);
                    l[i]=lab[0];a[i]=lab[1];b[i]=lab[2];
                }
            }
            // A low-cost, multi-channel edge estimate steers site positioning.
            // This is photo evidence, not a facial landmark classifier.
            for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*w+x;if(!valid[i])continue;
                int j=i+1,k=i-1,m=i+w,z=i-w;
                float e=0;
                if(valid[j]&&valid[k])e=Math.max(e,(float)Math.sqrt(colorD(j,k)));
                if(valid[m]&&valid[z])e=Math.max(e,(float)Math.sqrt(colorD(m,z)));
                edge[i]=Math.min(1f,e/65f);
            }
        }
        float colorD(int i,int j){float dl=l[i]-l[j],da=a[i]-a[j],db=b[i]-b[j];return dl*dl+da*da+db*db;}
    }

    static final class Seed {
        float x,y,L,A,B;
        Seed(float x,float y,Pixels p){this.x=x;this.y=y;int i=StyleKit.clampI((int)y,0,p.h-1)*p.w+StyleKit.clampI((int)x,0,p.w-1);
            L=p.l[i];A=p.a[i];B=p.b[i];}
    }

    /** Bounded feature-aware seed placement: regular baseline sites for smooth
     * areas, small shifts toward photographic edges for eyes / mouth / contours.
     * SLIC's Lab distance (not the seed alone) ultimately determines boundaries. */
    static ArrayList<Seed> refineSites(Pixels p,Options o){
        int target=Math.max(8,Math.min(o.maxPieces,p.validCount));
        float step=(float)Math.sqrt(p.validCount/(double)target);
        // Physical minimum spacing prevents thousands of impossible glass pieces.
        step=Math.max(step,Math.max(2f,o.minSpacingMm/p.px*.73f));
        ArrayList<Seed> seeds=new ArrayList<>();
        // Deterministic spatial jitter avoids the checkerboard appearance of a
        // fixed seed lattice without sacrificing minimum physical spacing.
        Random jitter=new Random(o.seed);
        for(float y=step*.5f;y<p.h;y+=step){CancellationCheckpoint.check();
            if(((int)y&15)==0)StyleKit.checkCancelled();
            for(float x=step*.5f;x<p.w;x+=step){CancellationCheckpoint.check();
                float px=StyleKit.clampF(x+(jitter.nextFloat()-.5f)*step*.60f,0,p.w-1);
                float py=StyleKit.clampF(y+(jitter.nextFloat()-.5f)*step*.60f,0,p.h-1);
                int bx=-1,by=-1;float best=-Float.MAX_VALUE;
                int rad=Math.max(1,Math.round(step*.47f));
                for(int yy=Math.max(0,(int)y-rad);yy<=Math.min(p.h-1,(int)y+rad);yy++)
                    for(int xx=Math.max(0,(int)x-rad);xx<=Math.min(p.w-1,(int)x+rad);xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
                        int i=yy*p.w+xx;if(!p.valid[i])continue;
                        float d2=(xx-px)*(xx-px)+(yy-py)*(yy-py);
                        float score=p.edge[i]*Math.max(.1f,o.edgeBias)*Math.min(2f,step*.18f)-d2/(step*step);
                        if(score>best){best=score;bx=xx;by=yy;}
                    }
                if(bx>=0)seeds.add(new Seed(bx+.5f,by+.5f,p));
            }
        }
        if(seeds.size()<3)throw new IllegalArgumentException("Too few panes in the Snip; increase image size");
        // Keep edge-aware work bounded even for tiny spacing values.
        if(seeds.size()>o.maxPieces){
            ArrayList<Seed> sampled=new ArrayList<>();
            for(int k=0;k<o.maxPieces;k++)sampled.add(seeds.get((int)((k+.5)*seeds.size()/o.maxPieces)));
            return sampled;
        }
        return seeds;
    }

    /** Local five-dimensional Lab+XY clustering. Uses an explicit mask and a
     * bounded search footprint, rather than assigning an unbounded full photo
     * to every center. Returns per-working-pixel region IDs. */
    static int[] segment(Pixels p,ArrayList<Seed> seeds){
        int n=p.w*p.h;int[] owner=new int[n];float[] d2=new float[n];
        float step=(float)Math.sqrt(p.validCount/(double)seeds.size());
        float spatial=9f*9f/Math.max(1f,step*step);
        for(int iteration=0;iteration<6;iteration++){if(((iteration)&63)==0)CancellationCheckpoint.check();
            Arrays.fill(owner,-1);Arrays.fill(d2,Float.POSITIVE_INFINITY);
            for(int j=0;j<seeds.size();j++){if(((j)&63)==0)CancellationCheckpoint.check();
                if((j&31)==0)StyleKit.checkCancelled();
                Seed s=seeds.get(j);
                int reach=Math.max(3,(int)Math.ceil(2.6f*step));
                int x0=Math.max(0,(int)s.x-reach),x1=Math.min(p.w-1,(int)s.x+reach);
                int y0=Math.max(0,(int)s.y-reach),y1=Math.min(p.h-1,(int)s.y+reach);
                for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                    int i=y*p.w+x;if(!p.valid[i])continue;
                    float dl=p.l[i]-s.L,da=p.a[i]-s.A,db=p.b[i]-s.B;
                    float dx=x+.5f-s.x,dy=y+.5f-s.y;
                    float dist=dl*dl+da*da+db*db+spatial*(dx*dx+dy*dy);
                    if(dist<d2[i]){d2[i]=dist;owner[i]=j;}
                }
            }
            // A seed at a narrow Snip extremity may leave an otherwise valid
            // pixel just outside all windows: attach it to its nearest seed.
            for(int i=0;i<n;i++)if(p.valid[i]&&owner[i]<0){
                int x=i%p.w,y=i/p.w;int best=0;float dd=Float.MAX_VALUE;
                for(int j=0;j<seeds.size();j++){if(((j)&63)==0)CancellationCheckpoint.check();
                    Seed s=seeds.get(j);float dx=s.x-x,dy=s.y-y;float d=dx*dx+dy*dy;
                    if(d<dd){dd=d;best=j;}
                }owner[i]=best;
            }
            int k=seeds.size();double[] sx=new double[k],sy=new double[k],sl=new double[k],sa=new double[k],sb=new double[k];int[] c=new int[k];
            for(int i=0;i<n;i++)if(owner[i]>=0){int j=owner[i];c[j]++;sx[j]+=i%p.w+.5;sy[j]+=i/p.w+.5;sl[j]+=p.l[i];sa[j]+=p.a[i];sb[j]+=p.b[i];}
            for(int j=0;j<k;j++)if(c[j]>0){Seed s=seeds.get(j);s.x=(float)(sx[j]/c[j]);s.y=(float)(sy[j]/c[j]);s.L=(float)(sl[j]/c[j]);s.A=(float)(sa[j]/c[j]);s.B=(float)(sb[j]/c[j]);}
        }
        return owner;
    }

    /** Remove an actual physical half-lead clearance on both sides of every
     * border. Regions remain non-overlapping even when two panes use the same
     * vinyl HEX; no duplicative nested paths within a color sheet. */
    static boolean[] insetMask(Pixels p,int[] owners,float leadMm){
        int n=owners.length;boolean[] keep=new boolean[n];
        float radius=Math.max(.35f,leadMm*.5f)/p.px;
        int r=Math.max(1,(int)Math.ceil(radius));
        for(int y=0;y<p.h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
            if((y&31)==0)StyleKit.checkCancelled();
            for(int x=0;x<p.w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*p.w+x,label=owners[i];if(label<0)continue;
                boolean good=true;
                loop:for(int dy=-r;dy<=r;dy++)for(int dx=-r;dx<=r;dx++){if(((dx)&63)==0)CancellationCheckpoint.check();
                    // Pixel-center approximation of a circular erosion kernel.
                    if(dx*dx+dy*dy>(radius+.24f)*(radius+.24f))continue;
                    int xx=x+dx,yy=y+dy;
                    if(xx<0||yy<0||xx>=p.w||yy>=p.h||owners[yy*p.w+xx]!=label){good=false;break loop;}
                }
                keep[i]=good;
            }
        }
        return keep;
    }

    /** Ensure every label has one contiguous dominant core. All disconnected
     * fragments are reassigned by multi-source flood expansion from those
     * cores; the expansion is connected to the core that claimed it. This is
     * essential for high-frequency photos where independent SLIC Lab matches
     * otherwise produce hundreds of isolated cut islands per seed. */
    static int[] connectedOwners(Pixels p,int[] owners){
        final int n=owners.length;
        int maxLabel=-1;
        for(int i=0;i<n;i++)if(owners[i]>maxLabel)maxLabel=owners[i];
        if(maxLabel<0)return owners;
        int[] component=new int[n];Arrays.fill(component,-1);
        int[] queue=new int[n];
        ArrayList<Integer> counts=new ArrayList<>(), labels=new ArrayList<>();
        int[] dominant=new int[maxLabel+1];Arrays.fill(dominant,-1);
        for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            if((i&4095)==0)StyleKit.checkCancelled();
            if(owners[i]<0||component[i]>=0)continue;
            int label=owners[i],id=counts.size(),head=0,tail=1;
            queue[0]=i;component[i]=id;
            while(head<tail){
                int at=queue[head++],x=at%p.w;
                if(x>0&&owners[at-1]==label&&component[at-1]<0){component[at-1]=id;queue[tail++]=at-1;}
                if(x+1<p.w&&owners[at+1]==label&&component[at+1]<0){component[at+1]=id;queue[tail++]=at+1;}
                if(at>=p.w&&owners[at-p.w]==label&&component[at-p.w]<0){component[at-p.w]=id;queue[tail++]=at-p.w;}
                if(at+p.w<n&&owners[at+p.w]==label&&component[at+p.w]<0){component[at+p.w]=id;queue[tail++]=at+p.w;}
            }
            counts.add(tail);labels.add(label);
            if(dominant[label]<0||tail>counts.get(dominant[label]))dominant[label]=id;
        }
        int[] corrected=new int[n];Arrays.fill(corrected,-1);
        int head=0,tail=0;
        for(int i=0;i<n;i++)if(component[i]>=0){
            int id=component[i],label=labels.get(id);
            if(dominant[label]==id){corrected[i]=label;queue[tail++]=i;}
        }
        while(head<tail){
            if((head&4095)==0)StyleKit.checkCancelled();
            int at=queue[head++],x=at%p.w,label=corrected[at];
            if(x>0&&component[at-1]>=0&&corrected[at-1]<0){corrected[at-1]=label;queue[tail++]=at-1;}
            if(x+1<p.w&&component[at+1]>=0&&corrected[at+1]<0){corrected[at+1]=label;queue[tail++]=at+1;}
            if(at>=p.w&&component[at-p.w]>=0&&corrected[at-p.w]<0){corrected[at-p.w]=label;queue[tail++]=at-p.w;}
            if(at+p.w<n&&component[at+p.w]>=0&&corrected[at+p.w]<0){corrected[at+p.w]=label;queue[tail++]=at+p.w;}
        }
        // Disjoint Snips are independent physical islands. Keep their existing
        // ownership rather than filling any excluded pixels to connect them.
        for(int i=0;i<n;i++)if(component[i]>=0&&corrected[i]<0)corrected[i]=owners[i];
        return corrected;
    }

    /** Straight physical-distance contour offset. Raster erosion used previously
     * rounded a 0.6 mm lead to zero at six inches. Here the signed distance is
     * sampled at coarse pixel centres and the desired radius is interpolated by
     * marching squares to subpixel coordinates. Distance to the nearest exposed
     * grid-cell edge is measured in millimetres, including diagonal corners. */
    static ArrayList<float[]> extractPanes(Pixels p,int[] owners,boolean[] unused,
                                           int[] paneColors,StyleKit.Layer[] glass,Options o){
        final int n=p.w*p.h;boolean[] visited=new boolean[n];int[] q=new int[n];
        int[] stamp=new int[n];int serial=0;
        ArrayList<float[]> all=new ArrayList<>();
        final float cellArea=p.px*p.px*p.yScale, inset=o.leadMm*.5f;
        final int search=Math.max(2,(int)Math.ceil(inset/Math.min(p.px,p.px*p.yScale))+2);
        for(int initial=0;initial<n;initial++){if(((initial)&63)==0)CancellationCheckpoint.check();
            if((initial&4095)==0)StyleKit.checkCancelled();
            if(owners[initial]<0||visited[initial])continue;
            int label=owners[initial],front=0,back=1,minX=p.w,minY=p.h,maxX=0,maxY=0;
            visited[initial]=true;q[0]=initial;++serial;
            while(front<back){
                int i=q[front++],x=i%p.w,y=i/p.w;
                stamp[i]=serial;
                minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
                if(x>0&&owners[i-1]==label&&!visited[i-1]){visited[i-1]=true;q[back++]=i-1;}
                if(x+1<p.w&&owners[i+1]==label&&!visited[i+1]){visited[i+1]=true;q[back++]=i+1;}
                if(y>0&&owners[i-p.w]==label&&!visited[i-p.w]){visited[i-p.w]=true;q[back++]=i-p.w;}
                if(y+1<p.h&&owners[i+p.w]==label&&!visited[i+p.w]){visited[i+p.w]=true;q[back++]=i+p.w;}
            }
            if(back*cellArea<o.minPieceAreaMm2)continue;
            int pad=search+1,bw=maxX-minX+1+pad*2,bh=maxY-minY+1+pad*2;
            int ox=minX-pad,oy=minY-pad;
            float[] field=new float[bw*bh];
            for(int ly=0;ly<bh;ly++){if(((ly)&63)==0)CancellationCheckpoint.check();
                if((ly&31)==0)StyleKit.checkCancelled();
                int y=oy+ly;
                for(int lx=0;lx<bw;lx++){if(((lx)&63)==0)CancellationCheckpoint.check();
                    int x=ox+lx,idx=y*p.w+x;
                    boolean inside=x>=0&&x<p.w&&y>=0&&y<p.h&&stamp[idx]==serial;
                    // Cap the search radius; values farther away only need
                    // to stay on their corresponding side of the isocontour.
                    float best=(inset+Math.max(p.px,p.px*p.yScale)*2f);
                    int x0=Math.max(minX,x-search),x1=Math.min(maxX,x+search);
                    int y0=Math.max(minY,y-search),y1=Math.min(maxY,y+search);
                    for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++){if(((xx)&63)==0)CancellationCheckpoint.check();
                        int ci=yy*p.w+xx;
                        if(stamp[ci]!=serial)continue;
                        // Boundary of the union of cells belonging to the
                        // current connected pane. Edge distance is exact.
                        if(xx==0||stamp[ci-1]!=serial)
                            best=Math.min(best,segmentDistance(x,y,xx-.5f,yy-.5f,xx-.5f,yy+.5f,p));
                        if(xx+1==p.w||stamp[ci+1]!=serial)
                            best=Math.min(best,segmentDistance(x,y,xx+.5f,yy-.5f,xx+.5f,yy+.5f,p));
                        if(yy==0||stamp[ci-p.w]!=serial)
                            best=Math.min(best,segmentDistance(x,y,xx-.5f,yy-.5f,xx+.5f,yy-.5f,p));
                        if(yy+1==p.h||stamp[ci+p.w]!=serial)
                            best=Math.min(best,segmentDistance(x,y,xx-.5f,yy+.5f,xx+.5f,yy+.5f,p));
                    }
                    field[ly*bw+lx]=(inside?best:-best)-inset;
                }
            }
            ArrayList<float[]> outlines=TopographicLayersEngine.contours(field,bw,bh,0f,p.px);
            for(float[] poly:outlines){CancellationCheckpoint.check();
                if(poly.length<6)continue;
                // The marching-square field has a one-sample apron on each
                // side. Map that apron back into the photo coordinate frame:
                // without this normalization border panes can extend ~1px
                // outside the declared SVG artboard despite having an inset.
                final float photoW=p.w*p.px,photoH=p.h*p.px*p.yScale;
                final float sx=photoW/(photoW+2*p.px);
                final float sy=photoH/(photoH+2*p.px*p.yScale);
                for(int a=0;a<poly.length;a+=2){
                    if(((a)&63)==0)CancellationCheckpoint.check();
                    poly[a]=(poly[a]+ox*p.px+p.px)*sx;
                    poly[a+1]=((poly[a+1]+oy*p.px+p.px)*p.yScale)*sy;
                }
                poly=StyleKit.removeCollinear(poly,Math.min(.08f,p.px*.14f));
                poly=StyleKit.clipToArtboardOrOriginal(poly,p.px*p.w,p.px*p.h*p.yScale);
                if(poly.length<6||Math.abs(StyleKit.signedArea(poly))<.08f)continue;
                glass[paneColors[label]].loops.add(poly);all.add(poly);
            }
        }
        return all;
    }

    private static float segmentDistance(float x,float y,float ax,float ay,float bx,float by,Pixels p){
        float vx=(bx-ax)*p.px,vy=(by-ay)*p.px*p.yScale;
        float dx=(x-ax)*p.px,dy=(y-ay)*p.px*p.yScale;
        float t=(vx*vx+vy*vy)==0?0:StyleKit.clampF((dx*vx+dy*vy)/(vx*vx+vy*vy),0,1);
        return (float)Math.hypot(dx-t*vx,dy-t*vy);
    }

    /** Safety fallback for high-frequency texture that destroys all photo-Lab
     * panes. Fewer purely spatial cells retain useful portrait color sampling
     * without generating unweedable fragments or a lead-only export. */
    static int[] spatialOwners(Pixels p,ArrayList<Seed> sites,int budget){
        int count=Math.min(sites.size(),Math.min(96,Math.max(8,budget/2)));
        int[] owners=new int[p.w*p.h];Arrays.fill(owners,-1);
        for(int y=0;y<p.h;y++){if(((y)&63)==0)CancellationCheckpoint.check();
            if((y&15)==0)StyleKit.checkCancelled();
            for(int x=0;x<p.w;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int i=y*p.w+x;if(!p.valid[i])continue;
                int best=0;float distance=Float.POSITIVE_INFINITY;
                for(int j=0;j<count;j++){if(((j)&63)==0)CancellationCheckpoint.check();
                    Seed seed=sites.get((int)((j+.5)*sites.size()/count));
                    float dx=x+.5f-seed.x,dy=y+.5f-seed.y;
                    float d=dx*dx+dy*dy;
                    if(d<distance){distance=d;best=j;}
                }
                owners[i]=best;
            }
        }
        return owners;
    }

    public static StyleKit.Result generate(int[] argb,int w,int h,Options o){
        o.validate();
        if(!Float.isFinite(o.gamma)||o.gamma<=0||!Float.isFinite(o.contrast)||o.contrast<=0)
            throw new IllegalArgumentException("Invalid tone settings");
        // Options apply in RGB before segmentation, seed color matching and
        // glass-palette sampling. Source.tone() changes luminance only and had
        // no effect on this color-driven engine.
        int[] adjusted=argb;
        if(Math.abs(o.gamma-1f)>1e-5f||Math.abs(o.contrast-1f)>1e-5f){
            adjusted=argb.clone();
            for(int i=0;i<adjusted.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                if((i&4095)==0)StyleKit.checkCancelled();
                int c=argb[i];
                int r=Math.round(255f*StyleKit.toneCurve(((c>>>16)&255)/255f,o.gamma,o.contrast));
                int g=Math.round(255f*StyleKit.toneCurve(((c>>>8)&255)/255f,o.gamma,o.contrast));
                int b=Math.round(255f*StyleKit.toneCurve((c&255)/255f,o.gamma,o.contrast));
                adjusted[i]=(c&0xff000000)|(r<<16)|(g<<8)|b;
            }
        }
        StyleKit.Source src=new StyleKit.Source(adjusted,w,h,o.allowed,o.widthMm);
        if(!src.anyValid())throw new IllegalArgumentException("No usable pixels");
        Pixels p=new Pixels(src);
        if(p.validCount<12)throw new IllegalArgumentException("Snip is too small for cuttable glass");
        ArrayList<Seed> seeds=refineSites(p,o);
        int[] owner=connectedOwners(p,segment(p,seeds));
        int n=seeds.size();
        // Palette is derived from the owner map after connectivity correction.
        double[] rr=new double[n],gg=new double[n],bb=new double[n];int[] sizes=new int[n];
        for(int i=0;i<owner.length;i++)if(owner[i]>=0){
            int j=owner[i],c=p.rgb[i];sizes[j]++;rr[j]+=(c>>16)&255;gg[j]+=(c>>8)&255;bb[j]+=c&255;
        }
        int[] regionColors=new int[n];
        for(int j=0;j<n;j++)if(sizes[j]>0){
            regionColors[j]=StyleKit.boostSaturation(0xff000000|((int)(rr[j]/sizes[j])<<16)|((int)(gg[j]/sizes[j])<<8)|(int)(bb[j]/sizes[j]),o.saturationBoost);
        }
        int[] palette;
        if(o.palette!=null&&o.palette.length>0){palette=o.palette.clone();}
        else {
            int live=0;for(int size:sizes)if(size>0)live++;
            int[] colors=new int[live];float[] wt=new float[live];int k=0;
            for(int j=0;j<n;j++)if(sizes[j]>0){colors[k]=regionColors[j];wt[k++]=sizes[j];}
            palette=StyleKit.kmeans(colors,wt,o.glassColors,o.seed);
        }
        PhotoSwatchAssociation association=new PhotoSwatchAssociation(
            palette,palette.length,null,src,PhotoSwatchAssociation.PANES);
        int[] paneColors=new int[n];
        for(int j=0;j<n;j++)if(sizes[j]>0){
            paneColors[j]=association.choose(regionColors[j]);
        }
        StyleKit.Result res=new StyleKit.Result(src.widthMm,src.heightMm);res.paperArgb=o.paperArgb;
        StyleKit.Layer lead=new StyleKit.Layer("lead",o.leadArgb);
        StyleKit.Layer[] glass=new StyleKit.Layer[palette.length];
        for(int k=0;k<palette.length;k++)glass[k]=new StyleKit.Layer("glass-"+(k+1),palette[k]);
        ArrayList<float[]> panes=extractPanes(p,owner,null,paneColors,glass,o);
        if(panes.isEmpty()||panes.size()>o.maxPieces){
            // Do not quietly publish a lead-only file or thousands of
            // disconnected cuts. Preserve the original swatches, but rebuild
            // pane sites with spatial connectivity and a smaller safe budget.
            for(StyleKit.Layer layer:glass)layer.loops.clear();
            owner=connectedOwners(p,spatialOwners(p,seeds,o.maxPieces));
            int spatialLabels=0;for(int label:owner)spatialLabels=Math.max(spatialLabels,label+1);
            int[] spatialColors=new int[spatialLabels];
            int[] chosenCount=new int[spatialLabels];
            double[] sr=new double[spatialLabels],sg=new double[spatialLabels],sb=new double[spatialLabels];
            for(int i=0;i<owner.length;i++)if(owner[i]>=0){
                int j=owner[i],c=p.rgb[i];chosenCount[j]++;sr[j]+=(c>>16)&255;sg[j]+=(c>>8)&255;sb[j]+=c&255;
            }
            for(int j=0;j<spatialLabels;j++)if(chosenCount[j]>0){
                int c=0xff000000|((int)(sr[j]/chosenCount[j])<<16)|((int)(sg[j]/chosenCount[j])<<8)|(int)(sb[j]/chosenCount[j]);
                spatialColors[j]=association.choose(
                    StyleKit.boostSaturation(c,o.saturationBoost));
            }
            panes=extractPanes(p,owner,null,spatialColors,glass,o);
        }
        if(panes.isEmpty()||panes.size()>o.maxPieces)
            throw new IllegalArgumentException("No cuttable glass panes at this detail and lead width; reduce Lead thickness or increase artwork size");
        if(o.leadMode==LeadMode.CUTOUT){lead.loops.add(StyleKit.rect(0f,0f,src.widthMm,src.heightMm));lead.loops.addAll(panes);}
        else if(o.leadMode==LeadMode.UNDERLAY)lead.loops.add(StyleKit.rect(0f,0f,src.widthMm,src.heightMm));
        if(o.leadMode!=LeadMode.NONE)res.layers.add(lead);
        for(StyleKit.Layer layer:glass)if(!layer.loops.isEmpty())res.layers.add(layer);
        res.summary="Edge-aware stained glass: "+panes.size()+" physical panes, "+palette.length+" glass swatches, lead "+o.leadMode;
        return res;
    }
}

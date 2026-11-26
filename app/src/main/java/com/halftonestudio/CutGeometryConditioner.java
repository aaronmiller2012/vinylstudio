package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Physical cut conditioning for one vinyl sheet of organic (raster-derived)
 * artwork. No Android dependencies.
 *
 * The sheet's even-odd vinyl region is rasterized on a fine physical grid and
 * morphologically repaired with exact Euclidean distance transforms:
 *   1. opening of the vinyl by the minimum cut width removes necks, slivers
 *      and specks that a blade cannot cut or a weeder cannot keep;
 *   2. waste narrower than the minimum weed gap is widened where it separates
 *      pieces, and filled where it is an isolated pinhole;
 *   3. a second opening removes any neck created by step 2.
 * The result is re-contoured from a signed distance field (sub-pixel, no
 * staircase) and simplified well below the cut tolerance. Only callers that
 * already found a physical defect on the sheet use this; exact geometric
 * styles keep their original polygons.
 */
final class CutGeometryConditioner {
    private CutGeometryConditioner(){}
    // Peak memory is ~25 bytes per pixel (masks, distance fields, contour
    // field); 2.5M pixels keeps one sheet near 60 MB on a phone heap.
    static final long MAX_PIXELS=2_500_000L;
    // A dense collection of long edges can cross millions of scan rows even
    // when pixel count is bounded. Each event stores four float coordinates;
    // reject expansion before allocating large scanline buckets.
    static final long MAX_EDGE_ROW_VISITS=1_000_000L;
    static final class BudgetExceededException extends RuntimeException {
        BudgetExceededException(String reason){super(reason);}
    }
    /** Inspector-guided repair rounds after the morphological pass. */
    static final int GUIDED_ROUNDS=4;
    private static final float INF=Float.MAX_VALUE/4;

    static ArrayList<float[]> condition(List<float[]> loops,float widthMm,float heightMm,float minMm,float gapMm){
        if(loops==null||loops.isEmpty())return new ArrayList<>();
        if(!(widthMm>0&&heightMm>0&&minMm>0&&gapMm>0)||!Float.isFinite(widthMm)||!Float.isFinite(heightMm))
            throw new IllegalArgumentException("Invalid cut conditioning dimensions");
        // Work only inside the sheet's occupied bounds (plus clearance).
        float lx=Float.POSITIVE_INFINITY,ly=lx,hx=Float.NEGATIVE_INFINITY,hy=hx;
        for(float[] loop:loops)for(int k=0;k+1<loop.length;k+=2){
            lx=Math.min(lx,loop[k]);hx=Math.max(hx,loop[k]);ly=Math.min(ly,loop[k+1]);hy=Math.max(hy,loop[k+1]);
        }
        float pad=2f*Math.max(minMm,gapMm);
        final float ox=Math.max(0f,lx-pad),oy=Math.max(0f,ly-pad);
        final float spanX=Math.min(widthMm,hx+pad)-ox,spanY=Math.min(heightMm,hy+pad)-oy;
        if(!(spanX>0&&spanY>0))return new ArrayList<>();
        // Grid aligned to the cut minimum: a 7-pixel disk is exactly the
        // minimum (+0.02 mm), so morphology neither keeps sub-minimum vinyl
        // nor erodes features that already meet the limit.
        final CutRepairGrid grid=CutRepairGrid.plan(spanX,spanY,minMm,gapMm,MAX_PIXELS);
        if(grid==null)throw new BudgetExceededException("Physical repair grid exceeds bounded resolution; retain original for cut review");
        final int w=grid.width,h=grid.height;
        final float p=grid.pitch;
        ArrayList<float[]> local=new ArrayList<>(loops.size());
        for(float[] loop:loops){
            float[] q=loop.clone();
            for(int k=0;k+1<q.length;k+=2){q[k]-=ox;q[k+1]-=oy;}
            local.add(q);
        }
        boolean[] vinyl=rasterize(local,w,h,p);
        StyleKit.checkCancelled();
        // Use a conservative integer footprint. Rounding up can turn a legal
        // minimum-width stroke into a missing stroke, especially near a seven-pixel
        // grid boundary. The final inspector, not morphology, decides residual safety.
        int rv=CutRepairGrid.openingRadius(minMm,p), rg=CutRepairGrid.openingRadius(gapMm,p);
        vinyl=open(vinyl,w,h,rv);
        StyleKit.checkCancelled();
        // Narrow waste that SEPARATES two pieces is widened to a full weed
        // gap; narrow waste that only notches into one piece (or is enclosed)
        // is filled. Repeat because widening can expose new narrow waste; the
        // final round fills everything that is still narrow.
        for(int round=0;round<3;round++){
            boolean[] waste=new boolean[w*h];
            for(int i=0;i<waste.length;i++)waste[i]=!vinyl[i];
            boolean[] wideWaste=open(waste,w,h,rg);
            boolean[] narrow=new boolean[w*h];
            boolean anyNarrow=false;
            for(int i=0;i<narrow.length;i++)if(waste[i]&&!wideWaste[i]){narrow[i]=true;anyNarrow=true;}
            if(!anyNarrow)break;
            int[] label=new int[w*h];
            int[] pieces=label8(vinyl,w,h);
            boolean[] widen=classifyNarrow(narrow,pieces,label,w,h);
            boolean[] grow=new boolean[w*h];
            boolean anyGrow=false;
            for(int i=0;i<narrow.length;i++){
                if(!narrow[i])continue;
                if(round<2&&widen[label[i]]){grow[i]=true;anyGrow=true;}
                else vinyl[i]=true;
            }
            if(anyGrow){
                float[] d=edt(grow,w,h);
                float lim=disk(rg);
                for(int i=0;i<vinyl.length;i++)if(vinyl[i]&&d[i]<=lim)vinyl[i]=false;
            }
            vinyl=open(vinyl,w,h,rv);
            StyleKit.checkCancelled();
        }
        // Opening cannot remove a pinched waist between two overlapping
        // disks. Pieces whose solid cores are disconnected are separate
        // pieces: split them along the boundary between their cores with a
        // full weed gap, then clean once more.
        if(splitPinches(vinyl,w,h,rv,rg))vinyl=open(vinyl,w,h,rv);
        // Lone specks barely at the minimum cannot be weeded reliably, and
        // enclosed pinholes barely at the gap are impractical to pick out.
        dropSmallComponents(vinyl,w,h,(int)Math.ceil(1.3f*minMm/p),true);
        dropSmallComponents(vinyl,w,h,(int)Math.ceil(1.3f*gapMm/p),false);
        StyleKit.checkCancelled();
        // Guided repair: the export inspector is the oracle. Cut a weed-gap
        // sized opening at every remaining neck/gap it reports, clean, retrace.
        ArrayList<float[]> out=trace(vinyl,w,h,p,ox,oy,widthMm,heightMm,minMm,gapMm);
        final int eraseR=(int)Math.ceil(Math.max(minMm,gapMm)/p)+1;
        for(int round=0;round<GUIDED_ROUNDS;round++){
            StyleKit.Result sheet=new StyleKit.Result(widthMm,heightMm);
            StyleKit.Layer layer=new StyleKit.Layer("conditioned",0xff000000);
            layer.loops.addAll(out);sheet.layers.add(layer);
            List<StyleCutGeometryPreflight.Warning> found;
            try{found=StyleCutGeometryPreflight.inspect(sheet,minMm,gapMm,4000);}
            catch(IllegalArgumentException invalid){break;}
            boolean erased=false;
            boolean[] spot=new boolean[w*h];
            for(StyleCutGeometryPreflight.Warning warning:found){
                if(warning.reason.contains("not verified")||warning.reason.contains("complexity"))continue;
                int cx=Math.round((warning.midXMm-ox)/p+.5f),cy=Math.round((warning.midYMm-oy)/p+.5f);
                if(cx<0||cy<0||cx>=w||cy>=h)continue;
                spot[cy*w+cx]=true;erased=true;
            }
            if(!erased)break;
            float[] fromSpot=edt(spot,w,h);
            float lim=disk(eraseR);
            for(int i=0;i<vinyl.length;i++)if(vinyl[i]&&fromSpot[i]<=lim)vinyl[i]=false;
            vinyl=open(vinyl,w,h,rv);
            dropSmallComponents(vinyl,w,h,(int)Math.ceil(1.3f*minMm/p),true);
            dropSmallComponents(vinyl,w,h,(int)Math.ceil(1.3f*gapMm/p),false);
            out=trace(vinyl,w,h,p,ox,oy,widthMm,heightMm,minMm,gapMm);
        }
        return out;
    }
    private static ArrayList<float[]> trace(boolean[] vinyl,int w,int h,float p,float ox,float oy,
                                            float widthMm,float heightMm,float minMm,float gapMm){
        // Signed distance (pixels), positive inside vinyl, zero midway between centres.
        boolean[] outside=new boolean[w*h];
        for(int i=0;i<outside.length;i++)outside[i]=!vinyl[i];
        float[] toWaste=edt(outside,w,h);outside=null;
        float[] field=new float[w*h];
        for(int i=0;i<field.length;i++)if(vinyl[i])field[i]=(float)Math.sqrt(toWaste[i])-.5f;
        toWaste=null;
        float[] toVinyl=edt(vinyl,w,h);
        for(int i=0;i<field.length;i++)if(!vinyl[i])field[i]=.5f-(float)Math.sqrt(toVinyl[i]);
        toVinyl=null;
        // A light blur of the distance field removes pixel staircases; it
        // preserves straight boundaries and moves curved ones sub-pixel.
        field=StyleKit.blur(field,w,h,.6f);
        // The border must stay outside so every contour closes.
        for(int x=0;x<w;x++){field[x]=-1;field[(h-1)*w+x]=-1;}
        for(int y=0;y<h;y++){field[y*w]=-1;field[y*w+w-1]=-1;}
        ArrayList<float[]> traced=TopographicLayersEngine.contours(field,w,h,0f,p);
        ArrayList<float[]> out=new ArrayList<>(traced.size());
        // Simplify to 0.04 mm: well inside blade and DXF flattening tolerance,
        // and it keeps sheets light for the cutter software and the inspector.
        float eps=Math.min(.04f,p*.35f);
        float minArea=minMm*minMm*.5f;
        for(float[] loop:traced){
            StyleKit.checkCancelled();
            float[] closed=Arrays.copyOf(loop,loop.length+2);
            closed[loop.length]=loop[0];closed[loop.length+1]=loop[1];
            float[] simple=StyleKit.rdp(closed,eps);
            if(simple.length>=8)simple=Arrays.copyOf(simple,simple.length-2);
            float[] keep=simple.length>=6&&StyleKit.selfIntersections(simple,1)==0?simple:loop;
            if(keep.length<6||StyleKit.selfIntersections(keep,1)>0)continue;
            if(Math.abs(StyleKit.signedArea(keep))<Math.min(minArea,gapMm*gapMm*.5f))continue;
            for(int k=0;k<keep.length;k+=2){
                keep[k]=Math.max(0f,Math.min(widthMm,keep[k]+ox));
                keep[k+1]=Math.max(0f,Math.min(heightMm,keep[k+1]+oy));
            }
            out.add(keep);
        }
        return out;
    }
    /** Removes 4-connected components of value {@code value} whose bounding
     * box is smaller than {@code spanPx} in both directions. Waste components
     * touching the border are the sheet background and are always kept. */
    private static void dropSmallComponents(boolean[] vinyl,int w,int h,int spanPx,boolean value){
        boolean[] seen=new boolean[vinyl.length];
        int[] stack=new int[1024];int[] members=new int[1024];
        for(int s=0;s<vinyl.length;s++){
            if(seen[s]||vinyl[s]!=value)continue;
            int top=0,count=0,x0=w,y0=h,x1=-1,y1=-1;boolean border=false;
            stack[top++]=s;seen[s]=true;
            while(top>0){
                int i=stack[--top];int x=i%w,y=i/w;
                if(count==members.length)members=Arrays.copyOf(members,count*2);
                members[count++]=i;
                x0=Math.min(x0,x);x1=Math.max(x1,x);y0=Math.min(y0,y);y1=Math.max(y1,y);
                if(x==0||y==0||x==w-1||y==h-1)border=true;
                int[] nb={x>0?i-1:-1,x<w-1?i+1:-1,y>0?i-w:-1,y<h-1?i+w:-1};
                for(int j:nb){
                    if(j<0||seen[j]||vinyl[j]!=value)continue;
                    seen[j]=true;
                    if(top==stack.length)stack=Arrays.copyOf(stack,top*2);
                    stack[top++]=j;
                }
            }
            if(!border&&x1-x0+1<spanPx&&y1-y0+1<spanPx)
                for(int k=0;k<count;k++)vinyl[members[k]]=!value;
        }
    }
    /** Pieces whose solid cores are disconnected are separate pieces joined
     * by a pinched waist: cut a full weed gap along the boundary between the
     * cores. Meshes whose cores reconnect elsewhere are left to the guided
     * repair pass. */
    private static boolean splitPinches(boolean[] vinyl,int w,int h,int rv,int rg){
        boolean[] outside=new boolean[vinyl.length];
        for(int i=0;i<vinyl.length;i++)outside[i]=!vinyl[i];
        float[] d=edt(outside,w,h);
        float lim=disk(rv);
        boolean[] core=new boolean[vinyl.length];
        boolean any=false;
        for(int i=0;i<vinyl.length;i++)if(vinyl[i]&&d[i]>lim){core[i]=true;any=true;}
        if(!any)return false;
        int[] comp=label8(core,w,h);
        int[] nearest=nearestSeed(core,w,h);
        int[] owner=new int[vinyl.length];
        for(int i=0;i<vinyl.length;i++)owner[i]=vinyl[i]&&nearest[i]>=0?comp[nearest[i]]:-1;
        boolean[] seam=new boolean[vinyl.length];
        boolean anySeam=false;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;if(owner[i]<0)continue;
            if((x+1<w&&owner[i+1]>=0&&owner[i+1]!=owner[i])||(y+1<h&&owner[i+w]>=0&&owner[i+w]!=owner[i])){
                seam[i]=true;anySeam=true;
            }
        }
        if(!anySeam)return false;
        float[] fromSeam=edt(seam,w,h);
        float cut=disk(rg+1);
        for(int i=0;i<vinyl.length;i++)if(vinyl[i]&&fromSeam[i]<=cut)vinyl[i]=false;
        return true;
    }
    /** 8-connected component ids (>=0) for true pixels, -1 elsewhere. */
    private static int[] label8(boolean[] set,int w,int h){
        int[] id=new int[set.length];Arrays.fill(id,-1);
        int[] stack=new int[1024];int next=0;
        for(int s=0;s<set.length;s++){
            if(!set[s]||id[s]>=0)continue;
            int top=0;stack[top++]=s;id[s]=next;
            while(top>0){
                int i=stack[--top];int x=i%w,y=i/w;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                    int xx=x+dx,yy=y+dy;
                    if(xx<0||yy<0||xx>=w||yy>=h)continue;
                    int j=yy*w+xx;
                    if(!set[j]||id[j]>=0)continue;
                    id[j]=next;
                    if(top==stack.length)stack=Arrays.copyOf(stack,top*2);
                    stack[top++]=j;
                }
            }
            next++;
        }
        return id;
    }
    /** Index of the nearest true pixel (Euclidean feature transform), -1 if none. */
    static int[] nearestSeed(boolean[] seed,int w,int h){
        float[] d=new float[w*h];int[] ny=new int[w*h];
        int n=Math.max(w,h);
        float[] f=new float[n],z=new float[n+1],out=new float[n];
        int[] v=new int[n],arg=new int[n];
        for(int x=0;x<w;x++){
            if((x&63)==0)StyleKit.checkCancelled();
            for(int y=0;y<h;y++)f[y]=seed[y*w+x]?0:INF;
            passArg(f,h,v,z,out,arg);
            for(int y=0;y<h;y++){d[y*w+x]=out[y];ny[y*w+x]=arg[y];}
        }
        int[] nearest=new int[w*h];
        for(int y=0;y<h;y++){
            if((y&63)==0)StyleKit.checkCancelled();
            System.arraycopy(d,y*w,f,0,w);
            passArg(f,w,v,z,out,arg);
            for(int x=0;x<w;x++){
                int sx=arg[x];
                nearest[y*w+x]=sx<0||out[x]>=INF?-1:ny[y*w+sx]*w+sx;
            }
        }
        return nearest;
    }
    private static void passArg(float[] f,int n,int[] v,float[] z,float[] out,int[] arg){
        int k=-1;
        for(int q=0;q<n;q++){
            if(f[q]>=INF)continue;
            if(k<0){k=0;v[0]=q;z[0]=-INF;z[1]=INF;continue;}
            double s=breakpoint(f,v[k],q);
            while(s<=z[k]){k--;s=breakpoint(f,v[k],q);}
            k++;v[k]=q;z[k]=(float)s;z[k+1]=INF;
        }
        if(k<0){for(int q=0;q<n;q++){out[q]=INF;arg[q]=-1;}return;}
        int j=0;
        for(int q=0;q<n;q++){
            while(z[j+1]<q)j++;
            float dq=q-v[j];
            out[q]=dq*dq+f[v[j]];arg[q]=v[j];
        }
    }
    /** Squared radius of a round digital disk: x*x+y*y <= r*r+r has no
     * single-pixel tips at the four compass points (a plain r*r disk does,
     * and those tips survive as spurs that close weed gaps). */
    private static float disk(int r){return r*(float)r+r+.01f;}
    /** Even-odd scanline fill sampled at pixel centres; pixel (i,j) centre is ((i-1+.5)p,(j-1+.5)p). */
    static boolean[] rasterize(List<float[]> loops,int w,int h,float p){
        boolean[] m=new boolean[w*h];
        // Bucket edges by the rows their y-range spans.
        int[] counts=new int[h+1];
        long edgeRowVisits=0;
        for(float[] loop:loops){
            int n=loop.length/2;
            for(int k=0;k<n;k++){
                float y0=loop[2*k+1],y1=loop[2*((k+1)%n)+1];
                if(y0==y1)continue;
                int a=Math.max(0,rowAbove(Math.min(y0,y1),p)),b=Math.min(h-1,rowBelow(Math.max(y0,y1),p));
                if(a>b)continue;
                edgeRowVisits+=(long)b-a+1;
                if(edgeRowVisits>MAX_EDGE_ROW_VISITS)
                    throw new BudgetExceededException("Cut geometry exceeds safe raster scan budget");
                for(int r=a;r<=b;r++)counts[r]++;
            }
        }
        int[] start=new int[h+1];
        for(int r=0;r<h;r++)start[r+1]=start[r]+counts[r];
        float[] ex0=new float[start[h]],ey0=new float[start[h]],ex1=new float[start[h]],ey1=new float[start[h]];
        int[] fill=Arrays.copyOf(start,h);
        for(float[] loop:loops){
            int n=loop.length/2;
            for(int k=0;k<n;k++){
                float x0=loop[2*k],y0=loop[2*k+1],x1=loop[2*((k+1)%n)],y1=loop[2*((k+1)%n)+1];
                if(y0==y1)continue;
                int a=Math.max(0,rowAbove(Math.min(y0,y1),p)),b=Math.min(h-1,rowBelow(Math.max(y0,y1),p));
                for(int r=a;r<=b;r++){int q=fill[r]++;ex0[q]=x0;ey0[q]=y0;ex1[q]=x1;ey1[q]=y1;}
            }
        }
        float[] xs=new float[64];
        for(int r=0;r<h;r++){
            if((r&63)==0)StyleKit.checkCancelled();
            float y=(r-1+.5f)*p;int c=0;
            for(int q=start[r];q<start[r+1];q++){
                if((ey0[q]>y)==(ey1[q]>y))continue;
                if(c==xs.length)xs=Arrays.copyOf(xs,c*2);
                xs[c++]=ex0[q]+(y-ey0[q])*(ex1[q]-ex0[q])/(ey1[q]-ey0[q]);
            }
            Arrays.sort(xs,0,c);
            for(int k=0;k+1<c;k+=2){
                int i0=Math.max(0,(int)Math.ceil(xs[k]/p+.5f)),i1=Math.min(w-1,(int)Math.floor(xs[k+1]/p+.5f));
                // centre x of pixel i is (i-.5)p: inside when xs[k] <= (i-.5)p < xs[k+1]
                for(int i=i0;i<=i1;i++){float cx=(i-.5f)*p;if(cx>=xs[k]&&cx<xs[k+1])m[r*w+i]=true;}
            }
        }
        for(int x=0;x<w;x++){m[x]=false;m[(h-1)*w+x]=false;}
        for(int y=0;y<h;y++){m[y*w]=false;m[y*w+w-1]=false;}
        return m;
    }
    private static int rowAbove(float y,float p){return (int)Math.floor(y/p+.5f);}
    private static int rowBelow(float y,float p){return (int)Math.ceil(y/p+.5f);}
    /** Morphological opening by a Euclidean disk of integer radius r (pixels). */
    static boolean[] open(boolean[] set,int w,int h,int r){
        boolean[] outside=new boolean[set.length];
        boolean any=false;
        for(int i=0;i<set.length;i++){outside[i]=!set[i];any|=set[i];}
        if(!any)return set.clone();
        float lim=disk(r);
        float[] d=edt(outside,w,h);
        boolean[] core=new boolean[set.length];
        boolean anyCore=false;
        for(int i=0;i<set.length;i++)if(set[i]&&d[i]>lim){core[i]=true;anyCore=true;}
        if(!anyCore)return new boolean[set.length];
        float[] e=edt(core,w,h);
        boolean[] out=new boolean[set.length];
        for(int i=0;i<set.length;i++)out[i]=set[i]&&e[i]<=lim;
        return out;
    }
    /** Squared Euclidean distance (pixels) from each pixel to the nearest true pixel. */
    static float[] edt(boolean[] seed,int w,int h){
        float[] d=new float[w*h];
        for(int i=0;i<d.length;i++)d[i]=seed[i]?0:INF;
        int n=Math.max(w,h);
        float[] f=new float[n],z=new float[n+1],out=new float[n];
        int[] v=new int[n];
        for(int x=0;x<w;x++){
            if((x&63)==0)StyleKit.checkCancelled();
            for(int y=0;y<h;y++)f[y]=d[y*w+x];
            pass(f,h,v,z,out);
            for(int y=0;y<h;y++)d[y*w+x]=out[y];
        }
        for(int y=0;y<h;y++){
            if((y&63)==0)StyleKit.checkCancelled();
            System.arraycopy(d,y*w,f,0,w);
            pass(f,w,v,z,out);
            System.arraycopy(out,0,d,y*w,w);
        }
        return d;
    }
    /** Felzenszwalb-Huttenlocher 1-D squared distance transform. */
    private static void pass(float[] f,int n,int[] v,float[] z,float[] out){
        // Lower envelope of parabolas rooted at the finite samples. Breakpoints
        // are computed in double: q*q reaches 1e7 and float would misplace them.
        int k=-1;
        for(int q=0;q<n;q++){
            if(f[q]>=INF)continue;
            if(k<0){k=0;v[0]=q;z[0]=-INF;z[1]=INF;continue;}
            double s=breakpoint(f,v[k],q);
            while(s<=z[k]){k--;s=breakpoint(f,v[k],q);}
            k++;v[k]=q;z[k]=(float)s;z[k+1]=INF;
        }
        if(k<0){for(int q=0;q<n;q++)out[q]=INF;return;}
        int j=0;
        for(int q=0;q<n;q++){
            while(z[j+1]<q)j++;
            float dq=q-v[j];
            out[q]=dq*dq+f[v[j]];
        }
    }
    private static double breakpoint(float[] f,int r,int q){
        return ((f[q]+(double)q*q)-(f[r]+(double)r*r))/(2.0*(q-r));
    }
    /** Label 4-connected narrow-waste components; true = widen, i.e. the
     * component borders at least two different vinyl pieces. */
    private static boolean[] classifyNarrow(boolean[] narrow,int[] pieces,int[] label,int w,int h){
        Arrays.fill(label,-1);
        ArrayList<Boolean> widen=new ArrayList<>();
        int[] stack=new int[1024];
        for(int s=0;s<narrow.length;s++){
            if(!narrow[s]||label[s]>=0)continue;
            int id=widen.size();int first=-1;boolean separates=false;int top=0;
            stack[top++]=s;label[s]=id;
            while(top>0){
                int i=stack[--top];int x=i%w,y=i/w;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                    int xx=x+dx,yy=y+dy;
                    if(xx<0||yy<0||xx>=w||yy>=h)continue;
                    int j=yy*w+xx;
                    int piece=pieces[j];
                    if(piece>=0){if(first<0)first=piece;else if(piece!=first)separates=true;continue;}
                    if((dx==0||dy==0)&&narrow[j]&&label[j]<0){
                        label[j]=id;
                        if(top==stack.length)stack=Arrays.copyOf(stack,top*2);
                        stack[top++]=j;
                    }
                }
            }
            widen.add(separates);
        }
        boolean[] out=new boolean[widen.size()];
        for(int i=0;i<out.length;i++)out[i]=widen.get(i);
        return out;
    }
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;

/**
 * One physical boundary graph for every source-color stencil layer.  A common
 * interface is smoothed ONCE and reused with reverse orientation by both
 * visible colors and all cumulative vinyl cut masks. This prevents the
 * independent curve fitting / support-layer fallback that left grid steps in
 * previous stencil exports. No external tracing implementation is copied.
 *
 * Grid topology remains authoritative (4-connected colors, even-odd holes).
 * Vertex positions and cubic controls are shared; color regions cannot open
 * gaps merely because they were traced at different points in the stack.
 */
final class SharedStencilBoundaries {
    private final int w,h,cols;
    private final double sx,sy;
    private final byte[] edge;
    private final float[] px,py;
    private int altered;
    private static final int N=1,E=2,S=4,W=8;
    private static final int[] DX={0,1,0,-1},DY={-1,0,1,0},BITS={N,E,S,W},OPP={S,W,N,E};
    private static void check(){if(Thread.currentThread().isInterrupted())throw new CancellationException("Shared stencil contour cancelled");}
    static final class PathData {
        final String data;
        final int loops;
        final boolean curved;
        PathData(String d,int n,boolean c){data=d;loops=n;curved=c;}
    }
    private SharedStencilBoundaries(int[] owner,int w,int h,double mmW,double mmH,double mmRadius,float detail){
        if(owner==null||w<1||h<1||owner.length!=(long)w*h)throw new IllegalArgumentException("Invalid stencil owners");
        this.w=w;this.h=h;cols=w+1;sx=mmW/w;sy=mmH/h;
        edge=new byte[cols*(h+1)];px=new float[edge.length];py=new float[edge.length];
        // Each interface is installed once, regardless of which of its two
        // colors is currently being converted to a cut path.
        for(int y=0;y<h;y++){
            if((y&31)==0)check();
            for(int x=0;x<w;x++){
                int c=owner[y*w+x];
                if(c!=(y==0?-1:owner[(y-1)*w+x]))link(y*cols+x,y*cols+x+1,1);
                if(c!=(x==0?-1:owner[y*w+x-1]))link(y*cols+x,(y+1)*cols+x,2);
                if(y==h-1 && c!=-1)link(h*cols+x,h*cols+x+1,1);
                if(x==w-1 && c!=-1)link(y*cols+w,(y+1)*cols+w,2);
            }
        }
        for(int id=0;id<edge.length;id++)if(edge[id]!=0){px[id]=id%cols;py[id]=id/cols;}
        // The smoothing is bounded in physical units and in grid cells. Its
        // arclength kernel removes pixel staircases without blurring a photo.
        double cell=Math.min(sx,sy);
        double sigma=Math.max(1.35,Math.min(4.4,mmRadius/Math.max(.0001,cell)*2.6));
        double movement=Math.min(1.05,Math.max(.32,mmRadius/Math.max(.0001,cell)*1.45));
        movement*=1.08-.22*Math.max(0,Math.min(1,detail));
        byte[] seen=new byte[edge.length];
        for(int id=0;id<edge.length;id++){
            if((id&8191)==0)check();
            if(degree(id)==2)continue;
            for(int d=0;d<4;d++)if((edge[id]&BITS[d])!=0&&(seen[id]&BITS[d])==0)
                smoothChain(walk(id,d,seen),false,sigma,movement);
        }
        for(int id=0;id<edge.length;id++){
            if((id&8191)==0)check();
            for(int d=0;d<4;d++)if((edge[id]&BITS[d])!=0&&(seen[id]&BITS[d])==0)
                smoothChain(walk(id,d,seen),true,sigma,movement);
        }
    }
    static SharedStencilBoundaries create(int[] owner,int w,int h,double widthMm,double heightMm,
                                          double smoothMm,float detail){
        if(smoothMm<=0||!Double.isFinite(smoothMm))throw new IllegalArgumentException("Smoothing must be positive");
        return new SharedStencilBoundaries(owner,w,h,widthMm,heightMm,smoothMm,detail);
    }
    private int degree(int p){return Integer.bitCount(edge[p]&15);}
    private void link(int a,int b,int dir){
        int ab=BITS[dir],ba=OPP[dir];
        edge[a]=(byte)(edge[a]|ab);edge[b]=(byte)(edge[b]|ba);
    }
    private int next(int p,int d){return p+DX[d]+DY[d]*cols;}
    private ArrayList<Integer> walk(int start,int first,byte[] seen){
        ArrayList<Integer> ids=new ArrayList<>();ids.add(start);
        int v=start,d=first,steps=0;
        do{
            if(++steps>edge.length*2)throw new IllegalStateException("Nonclosing shared boundary");
            int n=next(v,d);
            if(n<0||n>=edge.length||(edge[v]&BITS[d])==0||(edge[n]&OPP[d])==0)
                throw new IllegalStateException("Broken shared interface");
            seen[v]|=BITS[d];seen[n]|=OPP[d];ids.add(n);
            if(n==start||degree(n)!=2)break;
            int reverse=OPP[d];int nd=-1;
            for(int k=0;k<4;k++)if(BITS[k]!=reverse && (edge[n]&BITS[k])!=0){nd=k;break;}
            if(nd<0)throw new IllegalStateException("Non-manifold shared boundary");
            v=n;d=nd;
        }while(true);
        return ids;
    }
    private void smoothChain(ArrayList<Integer> ids,boolean cycle,double sigma,double maxShift){
        int n=ids.size();if(cycle && n>1 && ids.get(0).equals(ids.get(n-1))){ids.remove(n-1);n--;}
        if(n<10)return;
        double[] weights=new double[7];
        for(int j=0;j<weights.length;j++)weights[j]=Math.exp(-.5*j*j/(sigma*sigma));
        for(int i=0;i<n;i++){
            if((i&2047)==0)check();
            int v=ids.get(i),vx=v%cols,vy=v/cols;
            // Preserve artboard limits and multi-color junctions exactly.
            if(degree(v)!=2||vx==0||vy==0||vx==w||vy==h)continue;
            if(!cycle&&(i<3||i>=n-3))continue;
            double sum=weights[0],xx=vx*sum,yy=vy*sum;
            for(int d=1;d<weights.length;d++){
                int lo=i-d,hi=i+d;
                if(cycle){lo=(lo+n)%n;hi=hi%n;}
                else {lo=Math.max(0,lo);hi=Math.min(n-1,hi);}
                int a=ids.get(lo),b=ids.get(hi);
                double ww=weights[d];sum+=2*ww;xx+=(a%cols+b%cols)*ww;yy+=(a/cols+b/cols)*ww;
            }
            double dx=xx/sum-vx,dy=yy/sum-vy,mag=Math.hypot(dx,dy);
            if(mag<.02)continue;
            double factor=Math.min(1,maxShift/mag);
            // A boundary shared with more than two color regions is already
            // pinned. Keep neighboring sharp three-way junctions conservative.
            if(nearJunction(v))factor*=.6;
            px[v]=(float)Math.max(0,Math.min(w,vx+dx*factor));
            py[v]=(float)Math.max(0,Math.min(h,vy+dy*factor));
            altered++;
        }
    }
    private boolean nearJunction(int v){
        for(int d=0;d<4;d++)if((edge[v]&BITS[d])!=0 && degree(next(v,d))!=2)return true;
        return false;
    }
    int smoothedVertexCount(){return altered;}
    float vertexX(int x,int y){return px[y*cols+x];}
    float vertexY(int x,int y){return py[y*cols+x];}
    private static void fmt(StringBuilder b,double v){
        long value=Math.round(v*1000);if(value<0){b.append('-');value=-value;}
        b.append(value/1000).append('.');long rem=value%1000;
        if(rem<100)b.append('0');if(rem<10)b.append('0');b.append(rem);
    }
    private void point(StringBuilder b,double x,double y){fmt(b,x*sx);b.append(' ');fmt(b,y*sy);}
    private double[] tangent(int node,int toward){
        if(degree(node)!=2)return new double[]{px[toward]-px[node],py[toward]-py[node]};
        int other=-1;
        for(int d=0;d<4;d++)if((edge[node]&BITS[d])!=0){int q=next(node,d);if(q!=toward){other=q;break;}}
        if(other<0)return new double[]{px[toward]-px[node],py[toward]-py[node]};
        return new double[]{px[toward]-px[other],py[toward]-py[other]};
    }
    /**
     * Expand only grid edges on this shared graph. Each directed cubic has
     * exactly the same control geometry in reverse, independent of layer.
     */
    PathData path(ArrayList<ArrayList<int[]>> contours){
        StringBuilder out=new StringBuilder();boolean curved=false;
        for(ArrayList<int[]> loop:contours){
            if(loop.isEmpty())continue;
            int ox=loop.get(0)[0],oy=loop.get(0)[1],start=oy*cols+ox;
            if(start<0||start>=edge.length)throw new IllegalStateException("Shared contour start invalid");
            out.append('M');point(out,px[start],py[start]);
            int from=start,px0=ox,py0=oy;
            for(int span=0;span<loop.size();span++){
                int[] xy=loop.get((span+1)%loop.size());
                int dx=Integer.compare(xy[0],px0),dy=Integer.compare(xy[1],py0);
                if(dx!=0&&dy!=0)throw new IllegalStateException("Grid contour not axis aligned");
                int steps=Math.abs(xy[0]-px0)+Math.abs(xy[1]-py0);
                for(int j=0;j<steps;j++){
                    int to=from+dx+dy*cols;
                    int dir=dx==1?1:dx==-1?3:dy==1?2:0;
                    if((edge[from]&BITS[dir])==0)throw new IllegalStateException("Layer contour not on shared interface");
                    double ax=px[from],ay=py[from],bx=px[to],by=py[to];
                    double length=Math.hypot(bx-ax,by-ay);
                    if(length<=1e-8)throw new IllegalStateException("Collapsed shared boundary segment");
                    double[] ta=tangent(from,to),tb=tangent(to,from);
                    double taLen=Math.hypot(ta[0],ta[1]),tbLen=Math.hypot(tb[0],tb[1]);
                    double ux=bx-ax,uy=by-ay;
                    // For a reverse traversal, ta points toward 'to', while
                    // tb points toward 'from'. This makes reverse curves exact.
                    double cp=Math.min(.27,.30*length);
                    double c1x=ax+cp*ta[0]/Math.max(taLen,1e-8),c1y=ay+cp*ta[1]/Math.max(taLen,1e-8);
                    double c2x=bx+cp*tb[0]/Math.max(tbLen,1e-8),c2y=by+cp*tb[1]/Math.max(tbLen,1e-8);
                    if(!Double.isFinite(c1x)||!Double.isFinite(c2x))throw new IllegalStateException("Nonfinite curve");
                    out.append('C');point(out,c1x,c1y);out.append(' ');point(out,c2x,c2y);out.append(' ');point(out,bx,by);
                    curved=true;from=to;
                }
                px0=xy[0];py0=xy[1];
            }
            if(from!=start)throw new IllegalStateException("Open shared contour");
            out.append('Z');
        }
        return new PathData(out.toString(),contours.size(),curved);
    }
}

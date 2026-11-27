package com.halftonestudio;

import java.util.*;
import java.util.concurrent.CancellationException;

/** Deterministic, Android-free perfect maze. All dimensions are millimetres.
 * No TSP. Geometry is a union of lattice faces, with a topology-preserving warp.
 * Rectangular puzzle frame; nonrectangular Snips fail closed.
 */
public final class PerfectPortraitMaze {
    private PerfectPortraitMaze() {}
    public enum Mode { PORTRAIT, ORGANIC, HIDDEN, GEOMETRIC, CONTOUR, VORONOI_INSPIRED, VORTEX, MULTILAYER }
    private static final int Q=24, MAX_CELLS=14000, MAX_AXIS=180;
    private static final int[] DX={1,0,-1,0}, DY={0,1,0,-1};
    public static final class Options {
        public Mode mode=Mode.PORTRAIT;
        public double widthMm=279.4, minWallMm=.254, minPassageMm=.8;
        public int detail=85, difficulty=2;
        public long seed=566L;
    }
    public static final class Result {
        public final double widthMm,heightMm,wallFloorMm,passageFloorMm;
        public final int columns,rows,start,finish;
        private final int[] open,route;
        private final int sw,sh;
        private final Warp warp;
        private final List<float[]> walls;
        private Result(double w,double h,double wall,double passage,int nx,int ny,int a,int b,int[] graph,int[] path,int sw,int sh,Warp warp,List<float[]> loops){
            widthMm=w;heightMm=h;wallFloorMm=wall;passageFloorMm=passage;columns=nx;rows=ny;start=a;finish=b;open=graph;route=path;this.sw=sw;this.sh=sh;this.warp=warp;walls=loops;
        }
        public int[] passages(){return open.clone();}
        public float startYmm(){return (float)warp.at(0,(start/columns+.5)*Q)[1];}
        public boolean finishOnLeft(){return finish%columns==0;}
        public float finishYmm(){return (float)warp.at(finishOnLeft()?0:sw,(finish/columns+.5)*Q)[1];}
        public int[] solutionCells(){return route.clone();}
        public List<float[]> wallLoops(){ArrayList<float[]> copy=new ArrayList<>();for(float[] p:walls)copy.add(p.clone());return copy;}
        public float[] solutionMm(){
            ArrayList<Float> p=new ArrayList<>();double x=0,y=(start/columns+.5)*Q;
            append(p,warp.at(x,y));
            for(int v:route){double nx=(v%columns+.5)*Q,ny=(v/columns+.5)*Q;appendSegment(p,x,y,nx,ny);x=nx;y=ny;}
            appendSegment(p,x,y,finish%columns==0?0:sw,(finish/columns+.5)*Q);float[] a=new float[p.size()];for(int i=0;i<a.length;i++)a[i]=p.get(i);return a;
        }
        private void appendSegment(ArrayList<Float> p,double x,double y,double nx,double ny){int steps=(int)Math.max(Math.abs(nx-x),Math.abs(ny-y));for(int i=1;i<=steps;i++)append(p,warp.at(x+(nx-x)*i/steps,y+(ny-y)*i/steps));}
        private static void append(ArrayList<Float> p,double[] xy){p.add((float)xy[0]);p.add((float)xy[1]);}
        public String report(){return "Perfect maze: "+open.length+" cells; "+(open.length-1)+" edges; one solution, "+route.length+" cells; two marked exterior openings. Nominal lattice wall/passages >= "+String.format(Locale.ROOT,"%.3f / %.3f mm",wallFloorMm,passageFloorMm)+". Physical cut qualification separate.";}
        public String svg(boolean solved,int color){return svg(solved,color,0xffffffff);}
        public String svg(boolean solved,int color,int background){StringBuilder b=new StringBuilder();double m=Math.max(3,widthMm*.025);b.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(f(widthMm+2*m)).append("mm\" height=\"").append(f(heightMm+2*m)).append("mm\" viewBox=\"").append(f(-m)).append(' ').append(f(-m)).append(' ').append(f(widthMm+2*m)).append(' ').append(f(heightMm+2*m)).append("\"><rect x=\"").append(f(-m)).append("\" y=\"").append(f(-m)).append("\" width=\"").append(f(widthMm+2*m)).append("\" height=\"").append(f(heightMm+2*m)).append("\" fill=\"white\"/><rect x=\"0\" y=\"0\" width=\"").append(f(widthMm)).append("\" height=\"").append(f(heightMm)).append("\" fill=\"").append(String.format(Locale.ROOT,"#%06X",background&0xffffff)).append("\"/><path fill-rule=\"evenodd\" fill=\"").append(String.format(Locale.ROOT,"#%06X",color&0xffffff)).append("\" d=\"");for(float[] p:walls){path(b,p,true);}b.append("\"/>");float[] r=solutionMm();if(solved){b.append("<path fill=\"none\" stroke=\"#E34C31\" stroke-linejoin=\"round\" stroke-width=\"").append(f(passageFloorMm*.45)).append("\" d=\"");path(b,r,false);b.append("\"/>");}b.append("<g font-family=\"sans-serif\" font-size=\"").append(f(Math.min(m*.55,heightMm/rows*.45))).append("\" fill=\"#222\"><text x=\"").append(f(m*.1)).append("\" y=\"").append(f(r[1]-m*.12)).append("\">START</text><text text-anchor=\""+(finish%columns==0?"start":"end")+"\" x=\"").append(f(finish%columns==0?m*.1:widthMm-m*.1)).append("\" y=\"").append(f(r[r.length-1]-m*.12)).append("\">FINISH</text></g></svg>");return b.toString();}
    }
    private static String f(double v){return String.format(Locale.ROOT,"%.5f",v);}
    private static void path(StringBuilder b,float[] p,boolean closed){for(int i=0;i<p.length;i+=2)b.append(i==0?'M':'L').append(f(p[i])).append(' ').append(f(p[i+1]));if(closed)b.append('Z');}
    private static void cancel(){if(Thread.currentThread().isInterrupted())throw new CancellationException("Maze cancelled");}
    private static final class Edge {final int a,b,d; final double weight; Edge(int a,int b,int d,double w){this.a=a;this.b=b;this.d=d;weight=w;}}
    private static final class DSU {final int[] p,size;DSU(int n){p=new int[n];size=new int[n];for(int i=0;i<n;i++){p[i]=i;size[i]=1;}}int root(int x){while(x!=p[x]){p[x]=p[p[x]];x=p[x];}return x;}boolean join(int a,int b){a=root(a);b=root(b);if(a==b)return false;if(size[a]<size[b]){int t=a;a=b;b=t;}p[b]=a;size[a]+=size[b];return true;}}
    private static double lum(int c){double a=(c>>>24)/255.;return 1-a+a*(.2126*((c>>16)&255)+.7152*((c>>8)&255)+.0722*(c&255))/255.;}
    private static double sample(int[] img,int w,int h,double x,double y){int xx=Math.max(0,Math.min(w-1,(int)(x*w))),yy=Math.max(0,Math.min(h-1,(int)(y*h)));return lum(img[yy*w+xx]);}
    private static int neighbor(int i,int d,int nx,int ny){int x=i%nx+DX[d],y=i/nx+DY[d];return x<0||y<0||x>=nx||y>=ny?-1:y*nx+x;}
    private static void link(int[] g,Edge e){g[e.a]|=1<<e.d;g[e.b]|=1<<((e.d+2)%4);}
    private static int[] distances(int[] g,int nx,int ny,int start,int[] prev){int[] dist=new int[g.length],q=new int[g.length];Arrays.fill(dist,-1);Arrays.fill(prev,-1);int t=0,r=0;q[t++]=start;dist[start]=0;while(r<t){int v=q[r++];for(int d=0;d<4;d++)if((g[v]&(1<<d))!=0){int u=neighbor(v,d,nx,ny);if(u<0||(g[u]&(1<<((d+2)%4)))==0)throw new IllegalStateException("Broken graph edge");if(dist[u]<0){dist[u]=dist[v]+1;prev[u]=v;q[t++]=u;}}}return dist;}
    private static int[] solve(int[] g,int nx,int ny,int start,int end){int[] prev=new int[g.length];int[] ds=distances(g,nx,ny,start,prev);long degree=0;for(int i=0;i<g.length;i++){if(ds[i]<0)throw new IllegalStateException("Unreachable cell");degree+=Integer.bitCount(g[i]);}if(degree!=2L*(g.length-1))throw new IllegalStateException("Not a tree");int[] r=new int[ds[end]+1];for(int i=r.length-1,v=end;i>=0;i--,v=prev[v])r[i]=v;return r;}
    public static Result generate(int[] img,int w,int h,boolean[] allowed,Options o){
        if(o==null||o.mode==null||w<2||h<2||img==null||img.length!=(long)w*h||allowed!=null&&allowed.length!=img.length||!Double.isFinite(o.widthMm)||!Double.isFinite(o.minWallMm)||!Double.isFinite(o.minPassageMm)||o.widthMm<=0||o.widthMm>2000||o.minWallMm<=0||o.minPassageMm<=0||o.detail<0||o.detail>100||o.difficulty<0||o.difficulty>3)throw new IllegalArgumentException("Invalid maze settings");
        // Clipping an existing puzzle creates shortcuts. Do not silently ignore a crop.
        for(int i=0;i<img.length;i++){if((i&8191)==0)cancel();if((allowed!=null&&!allowed[i])||(img[i]>>>24)<128)throw new IllegalArgumentException("Maze requires a rectangular opaque crop; remove Snip or use a rectangular photo");}
        double height=o.widthMm*h/w;if(!Double.isFinite(height)||height>2000)throw new IllegalArgumentException("Maze dimensions too large");
        double safety=o.mode==Mode.PORTRAIT||o.mode==Mode.HIDDEN||o.mode==Mode.MULTILAYER?1:.60;
        double wall=o.minWallMm/safety+.04,pass=o.minPassageMm/safety+.04;
        double pitch=Math.max(3.2*(wall+pass), 8.0-5.5*o.detail/100.0);
        int nx=Math.min(MAX_AXIS,(int)(o.widthMm/pitch)),ny=Math.min(MAX_AXIS,(int)(height/pitch));
        while((long)nx*ny>MAX_CELLS){if(nx>=ny)nx--;else ny--;}
        if(o.mode==Mode.HIDDEN){nx-=nx%2;ny-=ny%2;}
        if(nx<3||ny<3)throw new IllegalArgumentException("Artwork too small for selected wall and passage widths");
        int n=nx*ny,sw=nx*Q,sh=ny*Q;double unit=Math.min(o.widthMm/sw,height/sh);
        int wallU=Math.max(2,2*(int)Math.ceil(wall/(2*unit))),passU=Math.max(2,2*(int)Math.ceil(pass/(2*unit)));
        if(2*wallU+passU>Q-2)throw new IllegalArgumentException("Cannot fit physical maze widths");
        double[] darkness=new double[n],gx=new double[n],gy=new double[n];int[] half=new int[n];
        for(int y=0;y<ny;y++){cancel();for(int x=0;x<nx;x++){int i=y*nx+x;double u=(x+.5)/nx,v=(y+.5)/ny;double l=0;for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++)l+=sample(img,w,h,u+a*.25/nx,v+b*.25/ny);darkness[i]=1-l/9;gx[i]=sample(img,w,h,u+.5/nx,v)-sample(img,w,h,u-.5/nx,v);gy[i]=sample(img,w,h,u,v+.5/ny)-sample(img,w,h,u,v-.5/ny);double d=o.mode==Mode.HIDDEN?.45:Math.pow(darkness[i],.85);half[i]=(int)Math.round((Q-wallU)/2.0*(1-d)+passU/2.0*d);if(x==0||y==0||x==nx-1||y==ny-1)half[i]=Math.min(half[i],Q/2-wallU);}}
        Random rng=new Random(o.seed);ArrayList<Edge> edges=new ArrayList<>(n*2);for(int i=0;i<n;i++){if((i&255)==0)cancel();for(int d=0;d<2;d++){int j=neighbor(i,d,nx,ny);if(j<0)continue;double u=(i%nx+.5)/nx-.5,v=(i/nx+.5)/ny-.5;double bias=0;switch(o.mode){case ORGANIC:bias=.35*Math.sin((d==0?v:u)*17);break;case GEOMETRIC:bias=.60*((i/nx/5+i%nx/5+d)%2);break;case CONTOUR:bias=1.8*Math.abs(d==0?gx[i]+gx[j]:gy[i]+gy[j]);break;case VORONOI_INSPIRED:bias=.25*Math.cos(24*u+17*v+d);break;case VORTEX:bias=.7*(d==0?Math.abs(u):Math.abs(v));break;case HIDDEN:bias=-.9*(darkness[i]+darkness[j]);break;default:bias=.15*Math.abs(darkness[i]-darkness[j]);}edges.add(new Edge(i,j,d,rng.nextDouble()+bias));}}
        edges.sort(Comparator.comparingDouble((Edge e)->e.weight).thenComparingInt(e->e.a).thenComparingInt(e->e.d));int[] g=new int[n];DSU dsu=new DSU(n);
        int start=ny/2*nx,end;
        if(o.mode==Mode.HIDDEN){int[] ends=hiddenBackbone(g,dsu,darkness,nx,ny);start=ends[0];end=ends[1];}
        else end=-1;
        for(Edge e:edges){if(dsu.join(e.a,e.b))link(g,e);}
        if(end<0){int[] prev=new int[n],dist=distances(g,nx,ny,start,prev);Integer[] ends=new Integer[ny];for(int y=0;y<ny;y++)ends[y]=y*nx+nx-1;Arrays.sort(ends,Comparator.comparingInt(i->dist[i]));end=ends[(int)Math.round((ny-1)*(.15+.28*o.difficulty))];}
        int[] route=solve(g,nx,ny,start,end);
        boolean[] clear=new boolean[Math.multiplyExact(sw,sh)];for(int i=0;i<n;i++){if((i&127)==0)cancel();int x=i%nx*Q+Q/2,y=i/nx*Q+Q/2,r=half[i];rect(clear,sw,x-r,y-r,x+r,y+r);for(int d=0;d<2;d++)if((g[i]&(1<<d))!=0){int j=neighbor(i,d,nx,ny),r2=Math.min(r,half[j]);if(d==0)rect(clear,sw,x,y-r2,x+Q,y+r2);else rect(clear,sw,x-r2,y,x+r2,y+Q);}}
        int sy=start/nx*Q+Q/2,ey=end/nx*Q+Q/2;rect(clear,sw,0,sy-half[start],Q/2,sy+half[start]);if(end%nx==0)rect(clear,sw,0,ey-half[end],Q/2,ey+half[end]);else rect(clear,sw,sw-Q/2,ey-half[end],sw,ey+half[end]);
        verifyRaster(clear,sw,sh,g,nx,ny,start,end);
        Warp warp=new Warp(o.mode,sw,sh,o.widthMm,height);List<float[]> loops=trace(clear,sw,sh,warp);if(loops.isEmpty()||loops.size()>5000)throw new IllegalStateException("Maze contour complexity exceeds cutter budget");
        return new Result(o.widthMm,height,wallU*unit*safety,passU*unit*safety,nx,ny,start,end,g,route,sw,sh,warp,loops);
    }
    /** Hidden picture: connect thresholded dark 2x2 blocks, prune unused light leaves,
     * splice each block's four-cycle along the block tree, then cut one exterior edge.
     * Remaining cells are attached without cycles by the normal Kruskal pass. */
    private static int[] hiddenBackbone(int[] g,DSU dsu,double[] dark,int nx,int ny){
        int cx=nx/2,cy=ny/2,n=cx*cy,root=(cy/2)*cx;double[] tone=new double[n];boolean[] required=new boolean[n],keep=new boolean[n];Arrays.fill(keep,true);
        for(int i=0;i<n;i++){int k=(i/cx*2)*nx+(i%cx*2);tone[i]=(dark[k]+dark[k+1]+dark[k+nx]+dark[k+nx+1])*.25;required[i]=tone[i]>=.52;}required[root]=true;
        ArrayList<Edge> edges=new ArrayList<>();for(int i=0;i<n;i++)for(int d=0;d<2;d++){int j=neighbor(i,d,cx,cy);if(j>=0)edges.add(new Edge(i,j,d,2-tone[i]-tone[j]+.00001*i));}
        edges.sort(Comparator.comparingDouble((Edge e)->e.weight).thenComparingInt(e->e.a).thenComparingInt(e->e.d));DSU coarse=new DSU(n);int[] tree=new int[n];for(Edge e:edges)if(coarse.join(e.a,e.b))link(tree,e);
        ArrayDeque<Integer> q=new ArrayDeque<>();for(int i=0;i<n;i++)if(!required[i]&&Integer.bitCount(tree[i])<=1)q.add(i);
        while(!q.isEmpty()){int i=q.remove();if(!keep[i]||required[i]||Integer.bitCount(tree[i])>1)continue;keep[i]=false;for(int d=0;d<4;d++)if((tree[i]&(1<<d))!=0){int j=neighbor(i,d,cx,cy);tree[j]&=~(1<<((d+2)%4));if(!required[j]&&Integer.bitCount(tree[j])<=1)q.add(j);}tree[i]=0;}
        for(int i=0;i<n;i++)if(keep[i]){int k=i/cx*2*nx+i%cx*2;link(g,new Edge(k,k+1,0,0));link(g,new Edge(k,k+nx,1,0));link(g,new Edge(k+1,k+nx+1,1,0));link(g,new Edge(k+nx,k+nx+1,0,0));}
        for(int i=0;i<n;i++)if(keep[i]){if((i&255)==0)cancel();int k=i/cx*2*nx+i%cx*2;if((tree[i]&1)!=0){int j=k+2;unlink(g,k+1,k+nx+1,1);unlink(g,j,j+nx,1);link(g,new Edge(k+1,j,0,0));link(g,new Edge(k+nx+1,j+nx,0,0));}if((tree[i]&2)!=0){int j=k+2*nx;unlink(g,k+nx,k+nx+1,0);unlink(g,j,j+1,0);link(g,new Edge(k+nx,j,1,0));link(g,new Edge(k+nx+1,j+1,1,0));}}
        int a=root/cx*2*nx,b=a+nx;unlink(g,a,b,1);
        int kept=0,walked=0;for(boolean v:keep)if(v)kept+=4;int cur=a,prev=-1;while(true){walked++;if(walked>kept)throw new IllegalStateException("Hidden route cycle");if(cur==b)break;int next=-1;for(int d=0;d<4;d++)if((g[cur]&(1<<d))!=0){int j=neighbor(cur,d,nx,ny);if(j!=prev){if(next>=0)throw new IllegalStateException("Hidden route branch");next=j;}}if(next<0||!dsu.join(cur,next))throw new IllegalStateException("Broken hidden route");prev=cur;cur=next;}if(walked!=kept)throw new IllegalStateException("Hidden route missed target cells");return new int[]{a,b};
    }
    private static void unlink(int[] g,int a,int b,int d){if((g[a]&(1<<d))==0||(g[b]&(1<<((d+2)%4)))==0)throw new IllegalStateException("Missing splice edge");g[a]&=~(1<<d);g[b]&=~(1<<((d+2)%4));}
    private static void rect(boolean[] m,int w,int x0,int y0,int x1,int y1){for(int y=y0;y<y1;y++)Arrays.fill(m,y*w+x0,y*w+x1,true);}
    private static void verifyRaster(boolean[] m,int sw,int sh,int[] g,int nx,int ny,int a,int b){
        for(int i=0;i<g.length;i++){if((i&255)==0)cancel();int x=i%nx*Q,y=i/nx*Q;for(int d=0;d<2;d++){int j=neighbor(i,d,nx,ny);if(j<0)continue;boolean found=false;for(int k=0;k<Q;k++){int pos=d==0?(y+k)*sw+x+Q-1:(y+Q-1)*sw+x+k;found|=m[pos]&&m[pos+(d==0?1:sw)];}if(found!=((g[i]&(1<<d))!=0))throw new IllegalStateException("Raster graph mismatch");}}
        int entry=0,exit=0;for(int y=0;y<sh;y++){if(m[y*sw]){if(y/Q==a/nx)entry++;else if(b%nx==0&&y/Q==b/nx)exit++;else throw new IllegalStateException("Unexpected left opening");}if(m[y*sw+sw-1]){if(b%nx!=nx-1||y/Q!=b/nx)throw new IllegalStateException("Unexpected right opening");exit++;}}for(int x=0;x<sw;x++)if(m[x]||m[(sh-1)*sw+x])throw new IllegalStateException("Unexpected boundary opening");if(entry==0||exit==0)throw new IllegalStateException("Missing opening");
    }
    private static final class Warp {final Mode mode;final int sw,sh;final double w,h;Warp(Mode m,int sw,int sh,double w,double h){mode=m;this.sw=sw;this.sh=sh;this.w=w;this.h=h;}
        double[] at(double x,double y){double X=x/sw*w,Y=y/sh*h,u=x/sw,v=y/sh,env=Math.sin(Math.PI*u)*Math.sin(Math.PI*v);double p=Math.min(w,h),a=.011*p,dx=0,dy=0;switch(mode){case ORGANIC:dx=a*Math.sin(6*Math.PI*v);dy=a*Math.sin(5*Math.PI*u);break;case GEOMETRIC:dx=a*.65*tri(3*v);dy=a*.65*tri(3*u);break;case CONTOUR:dx=a*Math.sin(4*Math.PI*v+2*Math.PI*u);dy=a*.6*Math.sin(4*Math.PI*u);break;case VORONOI_INSPIRED:dx=a*.55*Math.sin(7*Math.PI*v+4*Math.PI*u);dy=a*.55*Math.cos(7*Math.PI*u-4*Math.PI*v);break;case VORTEX:double U=u-.5,V=v-.5,t=8*(U*U+V*V);dx=-a*V*2*Math.sin(t);dy=a*U*2*Math.sin(t);break;default:break;}return new double[]{X+env*dx,Y+env*dy};}
        private double tri(double x){return 2/Math.PI*Math.asin(Math.sin(2*Math.PI*x));}}
    /** Boundary tracing of the exact union of occupied lattice squares. Edges point clockwise. */
    private static List<float[]> trace(boolean[] clear,int w,int h,Warp warp){int stride=w+1;byte[] e=new byte[Math.multiplyExact(stride,h+1)];for(int y=0;y<h;y++){if((y&31)==0)cancel();for(int x=0;x<w;x++){int i=y*w+x;if(clear[i])continue;int v=y*stride+x;if(y==0||clear[i-w])add(e,v,0);if(x==w-1||clear[i+1])add(e,v+1,1);if(y==h-1||clear[i+w])add(e,v+stride+1,2);if(x==0||clear[i-1])add(e,v+stride,3);}}
        ArrayList<float[]> out=new ArrayList<>();int[] delta={1,stride,-1,-stride};for(int s=0;s<e.length;s++){if((s&16383)==0)cancel();if(e[s]==0)continue;ArrayList<Float> p=new ArrayList<>();int v=s,prior=-1,steps=0;do{int bits=e[v]&255;if(Integer.bitCount(bits)!=1)throw new IllegalStateException("Ambiguous wall contour");int d=Integer.numberOfTrailingZeros(bits);if(prior!=d||warp.mode!=Mode.PORTRAIT&&warp.mode!=Mode.HIDDEN&&warp.mode!=Mode.MULTILAYER){double[] pt=warp.at(v%stride,v/stride);p.add((float)pt[0]);p.add((float)pt[1]);}e[v]=0;v+=delta[d];prior=d;if(++steps>e.length)throw new IllegalStateException("Open wall contour");}while(v!=s);if(p.size()<6)throw new IllegalStateException("Degenerate wall");float[] a=new float[p.size()];for(int k=0;k<a.length;k++)a[k]=p.get(k);out.add(a);}return out;}
    private static void add(byte[] e,int v,int d){if(e[v]!=0)throw new IllegalStateException("Touching wall contours");e[v]=(byte)(1<<d);}
}

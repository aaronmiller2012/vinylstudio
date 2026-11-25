package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Android-free, flat vinyl color separation. One physical cell belongs to at most one
 * vinyl layer; pale/background cells may instead expose the mounting paper.
 *
 * RGB is classified in CIE L*a*b* against the user's ACTUAL vinyl swatches and paper.
 * Boundaries are traced as directed grid edges, then neighboring equal-color pixels
 * are emitted as continuous paths with explicit paper openings (SVG even-odd fill).
 * No raster assets, clipping paths, linked images or gradients enter exported SVGs.
 *
 * Resolution is limited by physical spacing and a fixed sampling budget; this is a
 * cut-oriented approximation, not a high-resolution photographic segmentation model.
 */
public final class StencilSeparationEngine {
    private StencilSeparationEngine() {}
    public static final int MAX_CELLS=160_000;
    // A single cell is roughly 1..6 mm at typical default artwork sizes.
    private static final int[] DX={1,0,-1,0},DY={0,1,0,-1}; // right, down, left, up

    public static void generate(VinylEngine.Result result,int[] pixels,int width,int height){
        VinylEngine.Settings s=result.settings;
        if(s.mode!=VinylEngine.STENCIL)throw new IllegalArgumentException("Stencil mode required");
        float ww=s.widthMm,hh=result.heightMm,usableW=ww-2*s.marginMm,usableH=hh-2*s.marginMm;
        if(usableW<=0||usableH<=0)return;
        float step=Math.max(.45f,s.linkedControls?s.masterSpacingMm:s.layers.get(0).spacing);
        if(step>30)throw new IllegalArgumentException("Stencil detail is too coarse");
        int nx=Math.max(1,(int)Math.ceil(usableW/step)),ny=Math.max(1,(int)Math.ceil(usableH/step));
        if((long)nx*ny>MAX_CELLS)throw new IllegalArgumentException("Stencil contains too many samples. Increase Region detail spacing");
        float cw=usableW/nx,ch=usableH/ny;
        int sheetCount=s.layers.size();
        boolean withPaper=s.stencilPaperBackground;
        int[] swatches=new int[sheetCount+(withPaper?1:0)];
        boolean[] enabled=new boolean[swatches.length];
        for(int i=0;i<sheetCount;i++){
            swatches[i]=s.layers.get(i).color;
            enabled[i]=s.layers.get(i).enabled;
        }
        if(withPaper){swatches[sheetCount]=s.separationBackground;enabled[sheetCount]=true;}
        PhotoSwatchAssociation association=new PhotoSwatchAssociation(
            swatches,swatches.length,enabled,null,null,PhotoSwatchAssociation.STENCIL);
        int[] owners=new int[nx*ny];
        Arrays.fill(owners,-1);
        boolean snipped=s.snips!=null&&!s.snips.isEmpty();
        float[] cell=new float[8];
        for(int row=0;row<ny;row++)for(int col=0;col<nx;col++){if(((col)&63)==0)CancellationCheckpoint.check();
            if((col==0)&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            float x0=s.marginMm+col*cw,y0=s.marginMm+row*ch;
            float x1=s.marginMm+(col+1)*cw,y1=s.marginMm+(row+1)*ch;
            float x=(x0+x1)*.5f,y=(y0+y1)*.5f;
            // A circle crop excludes ENTIRE boundary cells before region tracing.
            // This keeps stencil region contours inside the physical circular artboard,
            // rather than rejecting a whole contiguous stencil region after tracing.
            if(s.circleCrop){
                float rx=ww/2f,ry=hh/2f;
                float[][] corners={{x0,y0},{x1,y0},{x1,y1},{x0,y1}};
                boolean inside=true;
                for(float[] p:corners){CancellationCheckpoint.check();float u=(p[0]-rx)/rx,v=(p[1]-ry)/ry;
                    if(u*u+v*v>1.00001f){inside=false;break;}}
                if(!inside)continue;
            }
            int px=pixels[Math.min(height-1,(int)(y/hh*height))*width+Math.min(width-1,(int)(x/ww*width))];
            int alpha=px>>>24;
            if(alpha<8)continue; // fully transparent photos expose paper, not black vinyl
            if(snipped){
                cell[0]=x0;cell[1]=y0;cell[2]=x1;cell[3]=y0;
                cell[4]=x1;cell[5]=y1;cell[6]=x0;cell[7]=y1;
                // Whole physical pixel must lie inside the allowed side of each
                // polygon; rejecting boundary cells is safer than cutting into snips.
                if(!s.snips.allowsPolygon(cell,ww,hh))continue;
            }
            int flattened=alpha==255?px:flatten(px,s.separationBackground);
            int choice=association.chooseCached(flattened);
            int assigned=choice==sheetCount?-1:choice;
            owners[row*nx+col]=assigned;
        }
        // Majority filtering only relabels already-allowed vinyl cells. It cannot
        // reintroduce cut geometry into paper, transparency or a removed snip.
        int passes=Math.max(0,Math.min(3,s.stencilCleanup));
        for(int pass=0;pass<passes;pass++){if(((pass)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            int[] next=owners.clone();
            for(int y=1;y<ny-1;y++)for(int x=1;x<nx-1;x++){if(((x)&63)==0)CancellationCheckpoint.check();
                int pos=y*nx+x,old=owners[pos];
                if(old<0)continue;
                int[] votes=new int[sheetCount];
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){if(((dx)&63)==0)CancellationCheckpoint.check();
                    int id=owners[(y+dy)*nx+x+dx];if(id>=0)votes[id]++;
                }
                int choice=old,count=0;
                for(int i=0;i<votes.length;i++)if(votes[i]>count){count=votes[i];choice=i;}
                if(choice!=old&&count>=6)next[pos]=choice;
            }
            owners=next;
        }
        if(passes>0)removeSpeckles(owners,nx,ny,passes);
        for(int li=0;li<s.layers.size();li++){if(((li)&63)==0)CancellationCheckpoint.check();
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            if(!s.layers.get(li).enabled)continue;
            emitContours(result,owners,nx,ny,li,cw,ch,s.marginMm, snipped?0:s.stencilSmoothing);
        }
    }

    private static int flatten(int px,int paper){
        int a=px>>>24,b=255-a;
        int r=(((px>>>16)&255)*a+((paper>>>16)&255)*b)/255;
        int g=(((px>>>8)&255)*a+((paper>>>8)&255)*b)/255;
        int bl=((px&255)*a+(paper&255)*b)/255;
        return 0xff000000|(r<<16)|(g<<8)|bl;
    }
    private static final class Lab {
        final double l,a,b;
        Lab(double l,double a,double b){this.l=l;this.a=a;this.b=b;}
    }
    private static final double[] SRGB_LINEAR=new double[256];
    static {for(int i=0;i<256;i++){if(((i)&63)==0)CancellationCheckpoint.check();double q=i/255.;SRGB_LINEAR[i]=q<=.04045?q/12.92:Math.pow((q+.055)/1.055,2.4);}}
    private static double linear(int v){return SRGB_LINEAR[v];}
    private static double f(double t){return t>.008856451679?t>0?Math.cbrt(t):0:(7.787037037*t+16./116.);}
    private static Lab lab(int color){
        double r=linear((color>>>16)&255),g=linear((color>>>8)&255),b=linear(color&255);
        double x=(.4124564*r+.3575761*g+.1804375*b)/.95047;
        double y=(.2126729*r+.7151522*g+.0721750*b);
        double z=(.0193339*r+.1191920*g+.9503041*b)/1.08883;
        double fx=f(x),fy=f(y),fz=f(z);
        return new Lab(116*fy-16,500*(fx-fy),200*(fy-fz));
    }
    private static double deltaSq(Lab a,Lab b){double dl=a.l-b.l,da=a.a-b.a,db=a.b-b.b;return dl*dl+da*da+db*db;}

    private static void removeSpeckles(int[] owners,int nx,int ny,int level){
        boolean[] seen=new boolean[owners.length];
        int[] stack=new int[owners.length];
        int threshold=level==1?1:level==2?3:6;
        for(int i=0;i<owners.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            int label=owners[i];if(label<0||seen[i])continue;
            int front=0,back=1;stack[0]=i;seen[i]=true;
            while(front<back){int pos=stack[front++],x=pos%nx,y=pos/nx;
                for(int d=0;d<4;d++){if(((d)&63)==0)CancellationCheckpoint.check();int xx=x+DX[d],yy=y+DY[d];
                    if(xx<0||xx>=nx||yy<0||yy>=ny)continue;
                    int next=yy*nx+xx;
                    if(!seen[next]&&owners[next]==label){seen[next]=true;stack[back++]=next;}
                }
            }
            if(back<=threshold)for(int k=0;k<back;k++)owners[stack[k]]=-1;
        }
    }
    private static final class Ring {
        final float[] vertices;
        final double signedArea;
        Ring(float[] p){vertices=p;signedArea=signedArea(p);}
    }
    private static void emitContours(VinylEngine.Result r,int[] owners,int nx,int ny,int li,
                                     float cw,float ch,float margin,float smoothing){
        int cols=nx+1,rows=ny+1;
        byte[] edges=new byte[cols*rows];
        for(int y=0;y<ny;y++)for(int x=0;x<nx;x++)if(owners[y*nx+x]==li){
            if(y==0||owners[(y-1)*nx+x]!=li)edges[y*cols+x]|=1;
            if(x==nx-1||owners[y*nx+x+1]!=li)edges[y*cols+x+1]|=2;
            if(y==ny-1||owners[(y+1)*nx+x]!=li)edges[(y+1)*cols+x+1]|=4;
            if(x==0||owners[y*nx+x-1]!=li)edges[(y+1)*cols+x]|=8;
        }
        ArrayList<Ring> exterior=new ArrayList<>(),holes=new ArrayList<>();
        int perimeterLimit=4*nx*ny+4;
        for(int v=0;v<edges.length;v++)while(edges[v]!=0){
            int firstDir=Integer.numberOfTrailingZeros(edges[v]&255);
            int cursor=v,dir=firstDir;
            ArrayList<Integer> vertices=new ArrayList<>();
            int count=0;
            do{
                vertices.add(cursor);
                if(++count>perimeterLimit)throw new IllegalArgumentException("Stencil boundary did not close");
                edges[cursor]&=~(1<<dir);
                int xx=cursor%cols+DX[dir],yy=cursor/cols+DY[dir];
                cursor=yy*cols+xx;
                if(cursor==v)break;
                int flags=edges[cursor]&255,choice=-1;
                int[] order={(dir+1)&3,dir,(dir+3)&3,(dir+2)&3};
                for(int d:order)if((flags&(1<<d))!=0){choice=d;break;}
                if(choice<0)throw new IllegalArgumentException("Open stencil contour");
                dir=choice;
            }while(true);
            float[] points=toContour(vertices,cols,cw,ch,margin);
            if(points.length<6)continue;
            points=bevel(points,cw,ch,smoothing);
            Ring ring=new Ring(points);
            if(Math.abs(ring.signedArea)<1e-4)continue;
            if(ring.signedArea>0)exterior.add(ring);else holes.add(ring);
        }
        // Positive winding in the downward-positive artboard is an exterior.
        // Associate each negative winding opening with its smallest containing outer.
        ArrayList<ArrayList<float[]>> joined=new ArrayList<>();
        for(Ring ring:exterior){CancellationCheckpoint.check();ArrayList<float[]> parts=new ArrayList<>();parts.add(ring.vertices);joined.add(parts);}
        for(Ring hole:holes){CancellationCheckpoint.check();int best=-1;double bestArea=Double.POSITIVE_INFINITY;
            float x=hole.vertices[0],y=hole.vertices[1];
            for(int i=0;i<exterior.size();i++){if(((i)&63)==0)CancellationCheckpoint.check();
                Ring outer=exterior.get(i);
                if(outer.signedArea<bestArea&&pointIn(outer.vertices,x,y)){
                    best=i;bestArea=outer.signedArea;
                }
            }
            if(best<0)throw new IllegalArgumentException("Unassigned stencil opening");
            joined.get(best).add(hole.vertices);
        }
        for(ArrayList<float[]> parts:joined){CancellationCheckpoint.check();
            if(VinylEngine.svgElementsForLayer(r,li)+parts.size()>5000)
                throw new IllegalArgumentException("Layer "+(li+1)+" exceeds 5,000 cut paths; increase stencil detail spacing or cleanup");
            // Single filled compound path can have several closed physical cut loops.
            // The renderer, metrics and SVG writer all use the SAME vector contours.
            r.shapes.get(li).add(VinylEngine.Shape.compound(parts.toArray(new float[0][])));
        }
    }
    private static float[] toContour(List<Integer> raw,int cols,float cw,float ch,float margin){
        int n=raw.size();if(n<3)return new float[0];
        ArrayList<Float> result=new ArrayList<>();
        for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            int prev=raw.get((i+n-1)%n),at=raw.get(i),next=raw.get((i+1)%n);
            int px=prev%cols,py=prev/cols,x=at%cols,y=at/cols,xx=next%cols,yy=next/cols;
            int ax=x-px,ay=y-py,bx=xx-x,by=yy-y;
            if(ax*by-ay*bx==0)continue; // Join collinear grid cells into one cut edge.
            result.add(margin+x*cw);result.add(margin+y*ch);
        }
        float[] pts=new float[result.size()];for(int i=0;i<pts.length;i++)pts[i]=result.get(i);
        return pts;
    }
    /** Bounded polygon chamfers remove hard staircase corners without SVG curves.
     * A zero value preserves the exact segmentation. Snipped projects force zero,
     * so smoothing cannot push vinyl back into an explicitly removed mask.
     */
    private static float[] bevel(float[] p,float cw,float ch,float amount){
        if(amount<=0||p.length<8)return p;
        float t=Math.max(0,Math.min(.25f,amount));
        int n=p.length/2;float[] out=new float[p.length*2];
        for(int i=0;i<n;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            int prev=(i+n-1)%n,next=(i+1)%n;
            float x=p[i*2],y=p[i*2+1];
            float ax=p[prev*2]-x,ay=p[prev*2+1]-y,bx=p[next*2]-x,by=p[next*2+1]-y;
            float al=(float)Math.hypot(ax,ay),bl=(float)Math.hypot(bx,by);
            float cut=Math.min(Math.min(al,bl)*.24f,Math.min(cw,ch)*t);
            out[4*i]=x+ax/al*cut;out[4*i+1]=y+ay/al*cut;
            out[4*i+2]=x+bx/bl*cut;out[4*i+3]=y+by/bl*cut;
        }
        return out;
    }
    private static double signedArea(float[] p){double a=0;for(int i=0;i<p.length;i+=2){if(((i)&63)==0)CancellationCheckpoint.check();int j=(i+2)%p.length;a+=p[i]*p[j+1]-p[j]*p[i+1];}return a*.5;}
    private static boolean pointIn(float[] p,float x,float y){boolean in=false;int n=p.length/2;
        for(int i=0,j=n-1;i<n;j=i++){if(((i)&63)==0)CancellationCheckpoint.check();
            float xi=p[2*i],yi=p[2*i+1],xj=p[2*j],yj=p[2*j+1];
            if(((yi>y)!=(yj>y))&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)in=!in;
        }
        return in;
    }
}

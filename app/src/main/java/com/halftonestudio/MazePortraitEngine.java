package com.halftonestudio;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Circular, portrait-biased, solvable maze.
 *
 * Walls are ONE Path object but may contain multiple contours; this is
 * mathematically different from one unbroken cutting stroke. The optional
 * solutionPath IS one continuous contour, connecting the maze entrance to exit.
 *
 * Geometry units: pixels in a square output canvas of side min(image W,H).
 * The source should already be cropped and positioned by PhotoCropEditor.
 */
public final class MazePortraitEngine {
    private MazePortraitEngine() {}

    private static final int RIGHT=1, DOWN=2, LEFT=4, UP=8;
    private static final int[] DX={1,0,-1,0};
    private static final int[] DY={0,1,0,-1};
    private static final int[] BIT={RIGHT,DOWN,LEFT,UP};
    private static final int[] OPP={LEFT,UP,RIGHT,DOWN};

    public static final class Options {
        /** 12..140; larger grids add portrait detail and cutting complexity. */
        public int gridSize = 54;
        /** More open passages in highlights, fewer in shadows. */
        public float highlightOpening = 0.32f;
        public float shadowOpening = 0.015f;
        /** Higher values bias the spanning tree toward brighter cells. */
        public float brightnessBias = 2.0f;
        /** Stable output from the same input/options. */
        public long seed = 20261023L;
        /** 0 easy, 1 medium, 2 hard, 3 expert. */
        public int difficulty = 1;
        public float diameterMm = 180f;
        public boolean drawSolution = false;
    }

    public static final class Result {
        public final Path wallsPath;
        public final Path solutionPath;
        public final Path circleOutlinePath;
        public final int gridSize, validCells, wallSegments;
        public final int entryCell, exitCell;
        public final float canvasSizePx, diameterMm;
        /** Inclusive bitmasks for open cell passages (1 right, 2 down, 4 left, 8 up). */
        public final int[] passages;
        public final boolean[] active;
        private final int[] solution;
        private final List<float[]> segments;

        private Result(Path walls, Path solutionPath, Path outline,
                       Options o, int cells, int wallsCount, int entry, int exit,
                       float side, int[] open, boolean[] valid, int[] solution,
                       List<float[]> segments) {
            this.wallsPath=walls;
            this.solutionPath=solutionPath;
            this.circleOutlinePath=outline;
            this.gridSize=o.gridSize;
            this.validCells=cells;
            this.wallSegments=wallsCount;
            this.entryCell=entry;
            this.exitCell=exit;
            this.canvasSizePx=side;
            this.diameterMm=o.diameterMm;
            this.passages=open.clone();
            this.active=valid.clone();
            this.solution=solution.clone();
            this.segments=segments;
        }

        public int[] solutionCells() { return solution.clone(); }
        public String summary(){return "Circular maze: "+validCells+" cells, "+wallSegments+
            " wall segments, "+solution.length+" solution cells; unique route.";}
        /** Combined preview vector with walls and highlighted solution. */
        public String toCombinedSvg(float wallStrokeMm,float routeStrokeMm){
            String walls=toWallSvg(wallStrokeMm);
            String sol=toSolutionSvg(routeStrokeMm);
            String body=sol.substring(sol.indexOf("<path"),sol.lastIndexOf("</svg>"))
                .replace("#000000","#E6673E");
            return walls.substring(0,walls.lastIndexOf("</svg>"))+body+"</svg>";
        }

        public void draw(Canvas canvas, Paint wallPaint) {
            if (canvas==null || wallPaint==null) throw new IllegalArgumentException();
            canvas.drawPath(wallsPath, wallPaint);
        }

        public void draw(Canvas canvas, Paint wallPaint, Paint solutionPaint) {
            draw(canvas, wallPaint);
            if (solutionPaint!=null) canvas.drawPath(solutionPath,solutionPaint);
        }

        /** Open-path wall SVG for pen/laser engraving, not closed vinyl geometry. */
        public String toWallSvg(float strokeMm) {
            if (!(strokeMm>0f) || !Float.isFinite(strokeMm))
                throw new IllegalArgumentException("Invalid stroke");
            float scale=diameterMm/canvasSizePx;
            StringBuilder b=svgStart();
            b.append("<path fill=\"none\" stroke=\"#000000\" stroke-width=\"")
             .append(fmt(strokeMm)).append("\" stroke-linecap=\"square\" d=\"");
            for(float[] e:segments) {CancellationCheckpoint.check();
                b.append("M").append(fmt(e[0]*scale)).append(" ").append(fmt(e[1]*scale))
                 .append("L").append(fmt(e[2]*scale)).append(" ").append(fmt(e[3]*scale));
            }
            return b.append("\"/></svg>").toString();
        }

        /** One continuous open polyline: entrance -> exit. */
        public String toSolutionSvg(float strokeMm) {
            if (!(strokeMm>0f) || !Float.isFinite(strokeMm))
                throw new IllegalArgumentException("Invalid stroke");
            float s=diameterMm/canvasSizePx, pitch=canvasSizePx/gridSize;
            StringBuilder b=svgStart();
            b.append("<path fill=\"none\" stroke=\"#000000\" stroke-width=\"")
             .append(fmt(strokeMm)).append("\" d=\"");
            for(int i=0;i<solution.length;i++){if(((i)&63)==0)CancellationCheckpoint.check();
                int cell=solution[i], col=cell%gridSize,row=cell/gridSize;
                b.append(i==0?'M':'L')
                 .append(fmt((col+0.5f)*pitch*s)).append(' ')
                 .append(fmt((row+0.5f)*pitch*s)).append(' ');
            }
            return b.append("\"/></svg>").toString();
        }

        private StringBuilder svgStart() {
            return new StringBuilder(256).append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(fmt(diameterMm)).append(' ').append(fmt(diameterMm))
                .append("\" width=\"").append(fmt(diameterMm))
                .append("mm\" height=\"").append(fmt(diameterMm)).append("mm\">");
        }
    }

    private static void checkCancelled(){
        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Superseded maze render");
    }
    public static Result generate(Bitmap portrait, Options o) {
        if(portrait==null || portrait.isRecycled() || o==null)
            throw new IllegalArgumentException("Bitmap and options required");
        if(o.gridSize<12 || o.gridSize>140 || o.difficulty<0 || o.difficulty>3 || !(o.diameterMm>0f) ||
           !Float.isFinite(o.diameterMm) ||
           !Float.isFinite(o.highlightOpening) || !Float.isFinite(o.shadowOpening) ||
           !Float.isFinite(o.brightnessBias))
            throw new IllegalArgumentException("Invalid maze settings");
        final int n=o.gridSize, count=n*n;
        final float side=Math.min(portrait.getWidth(),portrait.getHeight());
        if(side<24)throw new IllegalArgumentException("Image too small");
        final float pitch=side/n;
        final float radius=side*0.5f - pitch*0.8f;
        // Fit the square cell itself inside the circle, not just its center.
        final float safeRadius=radius - (float)(Math.sqrt(2)*pitch*0.5f);
        final float cx=side*0.5f,cy=cx;
        boolean[] active=new boolean[count];
        float[] lum=new float[count];
        int[] pixels=new int[portrait.getWidth()*portrait.getHeight()];
        portrait.getPixels(pixels,0,portrait.getWidth(),0,0,
                           portrait.getWidth(),portrait.getHeight());
        int ox=(portrait.getWidth()-(int)side)/2;
        int oy=(portrait.getHeight()-(int)side)/2;
        int validCount=0;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            if(x==0)checkCancelled();
            int k=y*n+x;
            float px=(x+0.5f)*pitch,py=(y+0.5f)*pitch;
            active[k]=Math.hypot(px-cx,py-cy)<=safeRadius;
            if(!active[k])continue;
            validCount++;
            int ix=Math.min(portrait.getWidth()-1,Math.max(0,ox+(int)px));
            int iy=Math.min(portrait.getHeight()-1,Math.max(0,oy+(int)py));
            int v=pixels[iy*portrait.getWidth()+ix];
            float a=((v>>>24)&255)/255f;
            lum[k]=(a*(0.2126f*((v>>>16)&255)
                        +0.7152f*((v>>>8)&255)+0.0722f*(v&255))
                    +(1f-a)*255f)/255f;
        }
        if(validCount<2)throw new IllegalArgumentException("No usable circular cells");
        int entry=-1,exit=-1;
        // Select leftmost and rightmost active cells near the vertical center.
        for(int x=0;x<n && entry<0;x++)
            for(int dy=0;dy<n;dy++){if(((dy)&63)==0)CancellationCheckpoint.check();
                int y=nearestCenterRow(dy,n),i=y*n+x;
                if(active[i]){entry=i;break;}
            }
        for(int x=n-1;x>=0 && exit<0;x--)
            for(int dy=0;dy<n;dy++){if(((dy)&63)==0)CancellationCheckpoint.check();
                int y=nearestCenterRow(dy,n),i=y*n+x;
                if(active[i]){exit=i;break;}
            }
        if(entry==exit)throw new IllegalStateException("Cannot create entrance and exit");

        int[] open=new int[count];
        boolean[] seen=new boolean[count];
        int[] stack=new int[validCount];
        Random random=new Random(o.seed);
        int sp=0;stack[sp++]=entry;seen[entry]=true;
        int reached=1;
        while(sp>0){
            checkCancelled();
            int at=stack[sp-1],x=at%n,y=at/n;
            int choice=-1;
            double weightSum=0;
            for(int d=0;d<4;d++){if(((d)&63)==0)CancellationCheckpoint.check();
                int nx=x+DX[d],ny=y+DY[d];
                if(nx<0||nx>=n||ny<0||ny>=n)continue;
                int k=ny*n+nx;
                if(!active[k]||seen[k])continue;
                double weight=0.15+Math.exp(Math.max(-6,Math.min(6,
                        o.brightnessBias*(lum[k]+lum[at]-1f))));
                weightSum+=weight;
                if(random.nextDouble()*weightSum<weight)choice=d;
            }
            if(choice<0){sp--;continue;}
            int next=(y+DY[choice])*n+x+DX[choice];
            open[at]|=BIT[choice];open[next]|=OPP[choice];
            seen[next]=true;reached++;
            stack[sp++]=next;
        }
        if(reached!=validCount)throw new IllegalStateException("Disconnected grid domain");
        // Open select extra corridors using local luminance. This can create
        // loops but never destroys connectivity or basic solvability.
        float lightProb=clamp(o.highlightOpening,0,0.9f);
        // Perfect-maze mode: the entrance-to-exit corridor has exactly one solution.
        // Optional extra openings are disabled even in expert mode: they
        // would undermine the portrait-revealing route.
        lightProb=0f;
        float darkProb=0f;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            int a=y*n+x;
            if(!active[a])continue;
            for(int d=0;d<2;d++){if(((d)&63)==0)CancellationCheckpoint.check();
                int nx=x+DX[d],ny=y+DY[d];
                if(nx>=n||ny>=n)continue;
                int b=ny*n+nx;
                if(!active[b] || (open[a]&BIT[d])!=0)continue;
                float brightness=(lum[a]+lum[b])*0.5f;
                float p=darkProb+(lightProb-darkProb)*brightness*brightness;
                if(random.nextFloat()<p){
                    open[a]|=BIT[d];open[b]|=OPP[d];
                }
            }
        }
        // Shortest solution in the finished maze.
        int[] prev=new int[count];
        Arrays.fill(prev,-1);
        ArrayDeque<Integer> queue=new ArrayDeque<>();
        queue.add(entry);prev[entry]=entry;
        while(!queue.isEmpty() && prev[exit]<0){
            checkCancelled();
            int a=queue.remove();
            for(int d=0;d<4;d++){if(((d)&63)==0)CancellationCheckpoint.check();
                if((open[a]&BIT[d])==0)continue;
                int b=a+DY[d]*n+DX[d];
                if(prev[b]>=0)continue;
                prev[b]=a;queue.add(b);
            }
        }
        if(prev[exit]<0)throw new IllegalStateException("Unsolvable maze");
        int len=1;
        for(int at=exit;at!=entry;at=prev[at])len++;
        int[] solution=new int[len];
        int at=exit;
        for(int i=len-1;i>=0;i--){if(((i)&63)==0)CancellationCheckpoint.check();solution[i]=at;at=prev[at];}
        Path solutionPath=new Path();
        for(int i=0;i<len;i++){if(((i)&63)==0)CancellationCheckpoint.check();
            int k=solution[i];
            float x=(k%n+0.5f)*pitch,y=(k/n+0.5f)*pitch;
            if(i==0)solutionPath.moveTo(x,y);else solutionPath.lineTo(x,y);
        }
        // Identify one wall on the circular perimeter at each endpoint and
        // remove it as the actual entrance/exit, not merely a colored marker.
        int entryDirection=perimeterOpening(entry,active,n);
        int exitDirection=perimeterOpening(exit,active,n);
        Path walls=new Path();
        List<float[]> segments=new ArrayList<>();
        // Every wall emitted once: top+left, plus bottom+right at boundaries.
        // Neighboring inactive cells create the circular silhouette.
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){if(((x)&63)==0)CancellationCheckpoint.check();
            int k=y*n+x;
            if(!active[k])continue;
            float x0=x*pitch,y0=y*pitch,x1=x0+pitch,y1=y0+pitch;
            if((y==0 || !active[k-n] || (open[k]&UP)==0) && !(k==entry&&entryDirection==3) && !(k==exit&&exitDirection==3))
                add(segments,walls,x0,y0,x1,y0);
            if((x==0 || !active[k-1] || (open[k]&LEFT)==0) && !(k==entry&&entryDirection==2) && !(k==exit&&exitDirection==2))
                add(segments,walls,x0,y0,x0,y1);
            if((y==n-1 || !active[k+n]) && !(k==entry&&entryDirection==1) && !(k==exit&&exitDirection==1))
                add(segments,walls,x0,y1,x1,y1);
            if((x==n-1 || !active[k+1]) && !(k==entry&&entryDirection==0) && !(k==exit&&exitDirection==0))
                add(segments,walls,x1,y0,x1,y1);
        }
        Path outline=new Path();
        outline.addCircle(cx,cy,safeRadius,Path.Direction.CW);
        Options saved=new Options();
        saved.gridSize=n;saved.diameterMm=o.diameterMm;
        return new Result(walls,solutionPath,outline,saved,validCount,
                segments.size(),entry,exit,side,open,active,solution,segments);
    }

    private static int perimeterOpening(int cell,boolean[] valid,int n){
        int x=cell%n,y=cell/n;
        // For entry/exit chosen near the left/right perimeter, prioritize outward.
        int[] directions=x<n/2?new int[]{2,3,1,0}:new int[]{0,3,1,2};
        for(int d:directions){CancellationCheckpoint.check();int xx=x+DX[d],yy=y+DY[d];
            if(xx<0||yy<0||xx>=n||yy>=n||!valid[yy*n+xx])return d;
        }
        throw new IllegalStateException("Entrance is not on the circular boundary");
    }
    private static void add(List<float[]> list,Path path,float x1,float y1,float x2,float y2){
        list.add(new float[]{x1,y1,x2,y2});
        path.moveTo(x1,y1);path.lineTo(x2,y2);
    }
    private static int nearestCenterRow(int offset,int n){
        int mid=(n-1)/2;
        if(offset==0)return mid;
        int step=(offset+1)/2;
        return Math.max(0,Math.min(n-1,mid+(offset%2==1?step:-step)));
    }
    private static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
    private static String fmt(float v){return String.format(Locale.US,"%.4f",v);}
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Android-independent, bounded space-filling centerline generator.
 * Canonical L-systems: Gosper, Sierpinski arrowhead, Peano switchback,
 * Wunderlich meander, and Moore. Coil/Meurthe are distinctly-oriented,
 * recursively continuous ternary traversals (family-inspired variants).
 *
 * Geometry is emitted once as a connected polyline, never as unrelated paths.
 * No implicit closePath, no overlapping cut edges, no Android APIs.
 */
public final class SpaceFillingCurves {
    private SpaceFillingCurves() {}
    public static final int GOSPER=0,PEANO=1,SIERPINSKI=2,WUNDERLICH=3,MOORE=4,COIL=5,MEURTHE=6;
    public static final String[] NAMES={"Gosper / Flowsnake","Peano / Switchback","Sierpiński / Arrowhead",
        "Wunderlich / Meander","Moore / Closed Loop","Coil / Ternary","Meurthe / Ternary"};
    public static final int MAX_POINTS=110000;
    public static final class Options {
        public int family=GOSPER,depth=3;
        public float width=400,height=400,padding=.035f,photoInfluence=.55f;
        public int[] pixels=null;public int imageWidth,imageHeight;
        public float rotationDegrees=0f,cornerRounding=0f;
        public boolean circularCrop=false;
        public Options copy(){Options o=new Options();o.family=family;o.depth=depth;o.width=width;o.height=height;
            o.padding=padding;o.photoInfluence=photoInfluence;o.pixels=pixels;o.imageWidth=imageWidth;o.imageHeight=imageHeight;
            o.rotationDegrees=rotationDegrees;o.cornerRounding=cornerRounding;o.circularCrop=circularCrop;return o;}
        public void validate(){
            if(family<GOSPER||family>MEURTHE||depth<1||depth>7||!Float.isFinite(width)||!Float.isFinite(height)||width<10||height<10||
                !Float.isFinite(padding)||padding<0||padding>.25f||!Float.isFinite(photoInfluence)||photoInfluence<0||photoInfluence>1||
                !Float.isFinite(rotationDegrees)||Math.abs(rotationDegrees)>360||!Float.isFinite(cornerRounding)||cornerRounding<0||cornerRounding>1)throw new IllegalArgumentException("Invalid curve settings");
            if(pixels!=null&&(imageWidth<1||imageHeight<1||pixels.length!=(long)imageWidth*imageHeight))throw new IllegalArgumentException("Invalid photo sample");
        }
    }
    public static final class Result {
        public final float[] xy;public final int family,depth;public final boolean closed;
        public final float shortestStep,longestStep;public final int vertices;
        private Result(float[] a,int f,int d,boolean c,float min,float max){xy=a;family=f;depth=d;closed=c;shortestStep=min;longestStep=max;vertices=a.length/2;}
    }
    /** Rewrap photographically modulated geometry with original carrier pitch.
     * This prevents small fold segments from falsely being treated as the
     * lattice pitch by vinyl manufacturing checks. */
    static Result remap(Result carrier,float[] points){
        if(points.length<4||(points.length&1)!=0)throw new IllegalArgumentException("Empty modulated curve");
        float longest=0f;
        for(int i=2;i<points.length;i+=2){
            float len=(float)Math.hypot(points[i]-points[i-2],points[i+1]-points[i-1]);
            if(!Float.isFinite(len)||len<=1e-5f)throw new IllegalArgumentException("Degenerate adaptive centerline");
            longest=Math.max(longest,len);
        }
        return new Result(points,carrier.family,carrier.depth,carrier.closed,carrier.shortestStep,longest);
    }
    private static final class Points {
        float[] xy=new float[8192];int size=0;
        void add(double x,double y){
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Cancelled curve generation");
            if(size>=MAX_POINTS)throw new IllegalArgumentException("Curve exceeds point budget; lower recursion depth");
            if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Nonfinite curve geometry");
            if(2*size+2>xy.length)xy=Arrays.copyOf(xy,Math.min(MAX_POINTS*2,xy.length*2));
            xy[2*size]=(float)x;xy[2*size+1]=(float)y;size++;
        }
        float[] finish(){return Arrays.copyOf(xy,size*2);}
    }
    /** Generate a mathematical carrier and optionally apply adaptive photographic geometry. */
    public static Result generate(Options o){
        o.validate();Points p=new Points();boolean closed=o.family==MOORE;
        switch(o.family){
            case GOSPER: lsystem(p,"A","A-B--B+A++AA+B-","+A-BB--B-A++A+B",o.depth,60,true,false);break;
            case SIERPINSKI: lsystem(p,"A","B-A-B","A+B+A",o.depth,60,true,false);break;
            case PEANO: lsystem(p,"R","LFRFL-F-RFLFR+F+LFRFL","RFLFR+F+LFRFL-F-RFLFR",o.depth,90,false,false);break;
            case WUNDERLICH: lsystem(p,"L","LF+RFR+FL-F-LFLFL-FRFR+","RF-LFL-FR+F+RFRFR+FLFL-",o.depth,90,false,false);break;
            case MOORE: lsystem(p,"LFL+F+LFL","-RF+LFL+FR-","+LF-RFR-FL+",o.depth,90,false,true);break;
            case COIL: ternary(p,o.depth,0);break;
            case MEURTHE: ternary(p,o.depth,1);break;
            default:throw new IllegalStateException("Unknown curve");
        }
        float[] normalized=fitAndModulate(p.finish(),o);
        // Physical pitch is measured BEFORE rounding: a rounded corner may
        // introduce shorter adjacent segments without reducing lattice spacing.
        float pitch=Float.MAX_VALUE;
        for(int i=2;i<normalized.length;i+=2)
            pitch=Math.min(pitch,(float)Math.hypot(normalized[i]-normalized[i-2],normalized[i+1]-normalized[i-1]));
        if(o.cornerRounding>.0001f)normalized=roundCorners(normalized,o.cornerRounding,closed);
        float min=Float.MAX_VALUE,max=0;
        for(int i=2;i<normalized.length;i+=2){
            float dist=(float)Math.hypot(normalized[i]-normalized[i-2],normalized[i+1]-normalized[i-1]);
            if(dist<=.00001f)throw new IllegalStateException("Curve contains a zero-length segment");
            min=Math.min(min,dist);max=Math.max(max,dist);
        }
        Result carrier=new Result(normalized,o.family,o.depth,closed,pitch,max);
        if(o.pixels!=null&&o.photoInfluence>.001f){
            AdaptiveCurvePortrait.Options portrait=new AdaptiveCurvePortrait.Options();
            portrait.pixels=o.pixels;portrait.width=o.imageWidth;portrait.height=o.imageHeight;
            portrait.influence=o.photoInfluence;
            return AdaptiveCurvePortrait.render(carrier,o.width,o.height,portrait);
        }
        return carrier;
    }
    private static void lsystem(Points p,String axiom,String a,String b,int depth,int degrees,boolean movesAB,boolean moore){
        int estimated=axiom.length();
        for(int i=0;i<depth;i++){
            estimated=(int)Math.min(350000,(long)estimated*(movesAB&&degrees==60?7:8));
        }
        String s=axiom;
        for(int round=0;round<depth;round++){
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Cancelled curve generation");
            StringBuilder next=new StringBuilder(Math.min(estimated,600000));
            for(int i=0;i<s.length();i++){
                char ch=s.charAt(i);
                if(ch=='A'||ch=='L')next.append(a);
                else if(ch=='B'||ch=='R')next.append(b);
                else next.append(ch);
                if(next.length()>900000)throw new IllegalArgumentException("L-system exceeds instruction budget");
            }
            s=next.toString();
        }
        double angle=0,x=0,y=0,delta=Math.PI*degrees/180.0;
        p.add(x,y);
        for(int i=0;i<s.length();i++){
            char ch=s.charAt(i);
            if(ch=='+')angle+=delta;
            else if(ch=='-')angle-=delta;
            else if(ch=='F'||movesAB&&(ch=='A'||ch=='B')){
                x+=Math.cos(angle);y+=Math.sin(angle);
                p.add(x,y);
            }
        }
        // Moore's canonical approximants are almost closed: the short missing
        // unit closure is part of the closed loop and must be represented.
        if(moore&&p.size>2){
            float dx=p.xy[0]-p.xy[(p.size-1)*2],dy=p.xy[1]-p.xy[(p.size-1)*2+1];
            if(Math.hypot(dx,dy)>1.01)throw new IllegalStateException("Moore loop has discontinuous closure");
            if(Math.hypot(dx,dy)>.00001)p.add(p.xy[0],p.xy[1]);
        }
    }
    /** A ternary recursive Hamiltonian traversal. Each recursive block has exact
     * corner endpoints and every lattice vertex is visited exactly once.
     * Coil favors repeated inward weaving; Meurthe alternates the local motif.
     * These are named ternary-inspired constructions, not claimed to reproduce
     * a unique canonical higher-dimensional definition of Coil/Meurthe. */
    private static void ternary(Points p,int depth,int variant){
        int side=pow3(depth);
        if((long)side*side>MAX_POINTS)throw new IllegalArgumentException("Ternary curve too complex; lower recursion depth");
        recursiveTernary(p,depth,0,0,0,2,variant,0);
    }
    private static int pow3(int depth){int n=1;for(int i=0;i<depth;i++)n*=3;return n;}
    private static volatile int[][][] templates=null;
    /** Enumerate the (at most two) 3x3 Hamiltonian paths between any pair of corners. */
    private static int[][] getPaths(int start,int end){
        if(templates==null){
            int[][][] cache=new int[81][][];
            int[] corners={0,2,6,8};
            for(int s:corners)for(int e:corners)if(s!=e){
                ArrayList<int[]> results=new ArrayList<>();
                walk3(s,e,1<<s,new int[]{s},results);
                if(results.isEmpty())throw new IllegalStateException("Missing 3x3 Hamiltonian template");
                cache[s*9+e]=results.toArray(new int[0][]);
            }
            templates=cache;
        }
        return templates[start*9+end];
    }
    private static void walk3(int position,int target,int visited,int[] route,ArrayList<int[]> out){
        if(route.length==9){if(position==target)out.add(route);return;}
        int x=position%3,y=position/3;
        for(int direction=0;direction<4;direction++){
            int xx=x+(direction==0?1:direction==1?-1:0),yy=y+(direction==2?1:direction==3?-1:0);
            if(xx<0||xx>=3||yy<0||yy>=3)continue;
            int next=yy*3+xx;
            if((visited&(1<<next))==0){int[] nextRoute=Arrays.copyOf(route,route.length+1);nextRoute[route.length]=next;
                walk3(next,target,visited|(1<<next),nextRoute,out);}
        }
    }
    private static void recursiveTernary(Points p,int depth,int offsetX,int offsetY,int startCorner,int endCorner,int variant,int salt){
        if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Cancelled curve generation");
        if(depth==1){
            int[][] candidates=getPaths(startCorner,endCorner);
            int choose=variant==0?(salt&1):((salt/3+salt*7)&1);
            int[] route=candidates[Math.floorMod(choose,candidates.length)];
            for(int cell:route)p.add(offsetX+cell%3,offsetY+cell/3);
            return;
        }
        int[][] candidates=getPaths(startCorner,endCorner);
        int first=variant==0?(salt&1):((salt/3+salt*7)&1);
        for(int candidate=0;candidate<candidates.length;candidate++){
            int[] route=candidates[(first+candidate)%candidates.length];
            int[] ent=new int[9],ex=new int[9];
            if(!findConnectors(route,0,startCorner,endCorner,ent,ex,variant,salt))continue;
            int tile=pow3(depth-1);
            for(int j=0;j<9;j++){
                int cell=route[j];
                recursiveTernary(p,depth-1,offsetX+(cell%3)*tile,offsetY+(cell/3)*tile,
                    ent[j],ex[j],variant,salt*19+j*13+cell+7);
            }
            return;
        }
        throw new IllegalStateException("No continuous ternary subdivision");
    }
    /** Choose only shared-face CORNER connectors; exclude equal entry/exit
     * corners (a Hamiltonian traversal cannot begin and end at one vertex). */
    private static boolean findConnectors(int[] route,int index,int entry,int finalCorner,int[] ent,int[] ex,int variant,int salt){
        ent[index]=entry;
        if(index==8){ex[index]=finalCorner;return entry!=finalCorner;}
        int a=route[index],b=route[index+1],dx=b%3-a%3,dy=b/3-a/3;
        int[][] pairs;
        if(dx==1)pairs=new int[][]{{2,0},{8,6}};
        else if(dx==-1)pairs=new int[][]{{0,2},{6,8}};
        else if(dy==1)pairs=new int[][]{{6,0},{8,2}};
        else if(dy==-1)pairs=new int[][]{{0,6},{2,8}};
        else throw new IllegalStateException("Broken macro adjacency");
        int first=variant==0?((index+salt)&1):((index*3+salt/5)&1);
        for(int k=0;k<2;k++){
            int[] pair=pairs[(first+k)&1];
            if(entry==pair[0])continue;
            ex[index]=pair[0];
            if(findConnectors(route,index+1,pair[1],finalCorner,ent,ex,variant,salt))return true;
        }
        return false;
    }
    private static float[] roundCorners(float[] xy,float strength,boolean closed){
        int n=xy.length/2;
        if(n<3)return xy;
        float[] rounded=xy.clone();float amount=Math.min(.18f,.17f*strength);
        for(int i=1;i<n-1;i++){
            int at=i*2;
            float ax=xy[at-2],ay=xy[at-1],x=xy[at],y=xy[at+1],bx=xy[at+2],by=xy[at+3];
            double before=Math.hypot(x-ax,y-ay),after=Math.hypot(bx-x,by-y);
            if(before<1e-5||after<1e-5)continue;
            float fx=(x-ax)/(float)before,fy=(y-ay)/(float)before,
                  tx=(bx-x)/(float)after,ty=(by-y)/(float)after;
            if(fx*tx+fy*ty>.985f)continue;
            float cut=amount*(float)Math.min(before,after);
            rounded[at]=x-fx*cut+tx*cut;
            rounded[at+1]=y-fy*cut+ty*cut;
        }
        return rounded;
    }
    private static float[] fitAndModulate(float[] xy,Options o){
        if(xy.length<4)throw new IllegalStateException("Empty curve");
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
        double radians=Math.toRadians(o.rotationDegrees),c=Math.cos(radians),s=Math.sin(radians);
        for(int i=0;i<xy.length;i+=2){float xx=xy[i],yy=xy[i+1];
            xy[i]=(float)(c*xx-s*yy);xy[i+1]=(float)(s*xx+c*yy);
            minX=Math.min(minX,xy[i]);maxX=Math.max(maxX,xy[i]);minY=Math.min(minY,xy[i+1]);maxY=Math.max(maxY,xy[i+1]);
        }
        float areaW=o.width*(1-2*o.padding),areaH=o.height*(1-2*o.padding);
        float scale=Math.min(areaW/Math.max(1e-3f,maxX-minX),areaH/Math.max(1e-3f,maxY-minY));
        float left=(o.width-scale*(maxX-minX))/2f,top=(o.height-scale*(maxY-minY))/2f;
        for(int i=0;i<xy.length;i+=2){xy[i]=left+(xy[i]-minX)*scale;xy[i+1]=top+(xy[i+1]-minY)*scale;}
        if(o.circularCrop){
            float cx=o.width*.5f,cy=o.height*.5f,maxR=0;
            for(int i=0;i<xy.length;i+=2)maxR=Math.max(maxR,(float)Math.hypot(xy[i]-cx,xy[i+1]-cy));
            float safe=(Math.min(o.width,o.height)*.5f)*(1f-Math.max(.095f,o.padding));
            float radialScale=Math.min(1f,safe/Math.max(1e-4f,maxR));
            for(int i=0;i<xy.length;i+=2){xy[i]=cx+(xy[i]-cx)*radialScale;xy[i+1]=cy+(xy[i+1]-cy)*radialScale;}
        }
        // Photographic density modulation is intentionally outside the
        // mathematical fit. AdaptiveCurvePortrait handles actual local
        // coverage changes rather than tiny point offsets.
        if(o.circularCrop){
            float cx=o.width*.5f,cy=o.height*.5f,safe=(Math.min(o.width,o.height)*.5f)*(1f-Math.max(.07f,o.padding));
            for(int i=0;i<xy.length;i+=2){
                float dx=xy[i]-cx,dy=xy[i+1]-cy,d=(float)Math.hypot(dx,dy);
                if(d>safe){xy[i]=cx+dx*safe/d;xy[i+1]=cy+dy*safe/d;}
            }
        }
        return xy;
    }
}

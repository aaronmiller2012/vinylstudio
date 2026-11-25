package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Conservative post-sampling repair for a modern variable-width ribbon.
 *
 * It never changes a valid ribbon. For a crossing created by offset outlines,
 * progressively reduces local HALF widths around the intersecting edges,
 * without moving its centerline or allowing an undersized vinyl neck.
 * If the centerline itself folds, or a legal minimum width prevents repair,
 * it returns the ORIGINAL polygon: downstream fail-closed cut validation owns it.
 * No polygon union, vertex deletion, smoothing, or path splitting takes place.
 */
final class LineRibbonRepair {
    private LineRibbonRepair() {}
    private static final int MAX_ATTEMPTS=16, MAX_CANDIDATES=5000;
    private static final long MAX_PAIR_TESTS=2_000_000L;
    private static final double CROSS_TOLERANCE=1e-12;

    static float[] outline(ArrayList<float[]> run) {
        int n=run.size();
        float[] xy=new float[n*4];
        for(int i=0;i<n;i++){
            if((i&63)==0)CancellationCheckpoint.check();
            float[] curr=run.get(i),before=run.get(Math.max(0,i-1)),after=run.get(Math.min(n-1,i+1));
            float dx=after[0]-before[0],dy=after[1]-before[1],length=(float)Math.hypot(dx,dy);
            if(!(length>.00001f))return null;
            float nx=-dy/length,ny=dx/length,half=curr[2];
            float bias=curr.length>3?curr[3]:0f;
            xy[i*2]=curr[0]+nx*half*(1f+bias);
            xy[i*2+1]=curr[1]+ny*half*(1f+bias);
            int j=2*n+2*(n-i-1);
            xy[j]=curr[0]-nx*half*(1f-bias);
            xy[j+1]=curr[1]-ny*half*(1f-bias);
        }
        return xy;
    }

    /** Returns unchanged original when unverified or physically unrepairable. */
    static float[] safeOutline(ArrayList<float[]> run,float minimumMm) {
        float[] original=outline(run);
        if(original==null||run.size()<3||run.size()>MAX_CANDIDATES||
                !(minimumMm>0)||!Float.isFinite(minimumMm))return original;
        Crossing first=findCrossing(original);
        if(first==null)return original;
        if(first.incomplete)return original;
        int n=run.size();
        float[] widths=new float[n],lower=new float[n];
        for(int i=0;i<n;i++){
            widths[i]=run.get(i)[2];
            lower[i]=minimumMm*.5f+.006f;
            if(!Float.isFinite(widths[i])||widths[i]<lower[i])return original;
        }
        for(int attempt=0;attempt<MAX_ATTEMPTS;attempt++){
            boolean touched=false;
            int[] vertices={first.edge1,(first.edge1+1)%(n*2),first.edge2,(first.edge2+1)%(n*2)};
            for(int vertex:vertices){
                int center=vertex<n?vertex:2*n-1-vertex;
                for(int i=Math.max(0,center-2);i<=Math.min(n-1,center+2);i++){
                    float prior=widths[i];
                    float next=Math.max(lower[i],prior*.76f);
                    if(next+.000001f<prior){widths[i]=next;touched=true;}
                }
            }
            if(!touched)return original;
            ArrayList<float[]> copy=new ArrayList<>(n);
            for(int i=0;i<n;i++){
                float[] sample=run.get(i),item=sample.clone();
                item[2]=widths[i];
                copy.add(item);
            }
            float[] candidate=outline(copy);
            if(candidate==null)return original;
            Crossing result=findCrossing(candidate);
            if(result==null)return candidate;
            if(result.incomplete)return original;
            first=result;
        }
        return original;
    }

    /** Package-private for host geometry probes; null = no proven proper crossing. */
    static Crossing findCrossing(float[] contour){
        int n=contour.length/2;
        if(n<4||n>MAX_CANDIDATES*2)return new Crossing(-1,-1,true);
        Edge[] edges=new Edge[n];
        for(int i=0;i<n;i++){
            int j=(i+1)%n;
            float ax=contour[2*i],ay=contour[2*i+1],bx=contour[2*j],by=contour[2*j+1];
            if(!Float.isFinite(ax)||!Float.isFinite(ay)||!Float.isFinite(bx)||!Float.isFinite(by))
                return new Crossing(-1,-1,true);
            edges[i]=new Edge(i,ax,ay,bx,by);
        }
        Arrays.sort(edges,(a,b)->Float.compare(a.minX,b.minX));
        long tested=0;
        for(int i=0;i<n;i++){
            if((i&63)==0)CancellationCheckpoint.check();
            Edge a=edges[i];
            for(int j=i+1;j<n;j++){
                Edge b=edges[j];
                if(b.minX>a.maxX)break;
                if(a.maxY<b.minY||b.maxY<a.minY)continue;
                int difference=Math.abs(a.index-b.index);
                if(difference==1||difference==n-1)continue;
                if(++tested>MAX_PAIR_TESTS)return new Crossing(-1,-1,true);
                if(proper(a.ax,a.ay,a.bx,a.by,b.ax,b.ay,b.bx,b.by))
                    return new Crossing(a.index,b.index,false);
            }
        }
        return null;
    }
    private static double cross(float ax,float ay,float bx,float by,float cx,float cy){
        return (double)(bx-ax)*(cy-ay)-(double)(by-ay)*(cx-ax);
    }
    private static boolean proper(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
        double x=cross(ax,ay,bx,by,cx,cy),y=cross(ax,ay,bx,by,dx,dy);
        double z=cross(cx,cy,dx,dy,ax,ay),w=cross(cx,cy,dx,dy,bx,by);
        return x*y<-CROSS_TOLERANCE&&z*w<-CROSS_TOLERANCE;
    }
    static final class Crossing {
        final int edge1,edge2;final boolean incomplete;
        Crossing(int a,int b,boolean unknown){edge1=a;edge2=b;incomplete=unknown;}
    }
    private static final class Edge {
        final int index;final float ax,ay,bx,by,minX,maxX,minY,maxY;
        Edge(int i,float ax,float ay,float bx,float by){
            index=i;this.ax=ax;this.ay=ay;this.bx=bx;this.by=by;
            minX=Math.min(ax,bx);maxX=Math.max(ax,bx);minY=Math.min(ay,by);maxY=Math.max(ay,by);
        }
    }
}

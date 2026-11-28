package com.halftonestudio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Conservative connected groups of contour bounds, including nested holes.
 * Bounds may over-group curves but never separate contours that could interact
 * within the chosen physical repair clearance. Used only on the bounded fallback. */
final class CutRepairPartitions {
    private CutRepairPartitions(){}
    static List<List<float[]>> group(List<float[]> loops,float pad){
        int n=loops.size();
        if(n>2000||!Float.isFinite(pad)||pad<0)throw new IllegalArgumentException("Invalid repair partition request");
        float[][] box=new float[n][4];int[] parent=new int[n];
        for(int i=0;i<n;i++){
            CancellationCheckpoint.check();
            parent[i]=i;float[] loop=loops.get(i);
            if(loop==null||loop.length<6||(loop.length&1)!=0)throw new IllegalArgumentException("Invalid contour");
            float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
            for(int k=0;k<loop.length;k+=2){
                if(!Float.isFinite(loop[k])||!Float.isFinite(loop[k+1]))throw new IllegalArgumentException("Invalid contour coordinate");
                minX=Math.min(minX,loop[k]);maxX=Math.max(maxX,loop[k]);
                minY=Math.min(minY,loop[k+1]);maxY=Math.max(maxY,loop[k+1]);
            }
            box[i][0]=minX;box[i][1]=minY;box[i][2]=maxX;box[i][3]=maxY;
        }
        for(int i=0;i<n;i++){
            CancellationCheckpoint.check();float[] a=box[i];
            for(int j=0;j<i;j++){
                float[] b=box[j];
                if(a[0]<=b[2]+pad&&b[0]<=a[2]+pad&&a[1]<=b[3]+pad&&b[1]<=a[3]+pad){
                    int x=find(parent,i),y=find(parent,j);if(x!=y)parent[Math.max(x,y)]=Math.min(x,y);
                }
            }
        }
        LinkedHashMap<Integer,List<float[]>> out=new LinkedHashMap<>();
        for(int i=0;i<n;i++)out.computeIfAbsent(find(parent,i),k->new ArrayList<>()).add(loops.get(i));
        return new ArrayList<>(out.values());
    }
    private static int find(int[] parent,int i){while(parent[i]!=i){parent[i]=parent[parent[i]];i=parent[i];}return i;}

    /** M8: split even-odd loops into whole nesting trees (a root loop with every loop nested in it)
     * that reach any window grown by {@code halo}, and the rest. Loops of one sheet do not cross,
     * so a tree whose loops all lie outside every grown window contributes nothing inside it and
     * can be kept byte-for-byte. Order within each output list follows the input order. */
    static void splitNestingTrees(List<float[]> loops,List<float[]> windows,float halo,List<float[]> touched,List<float[]> untouched){
        int n=loops.size();float[][] box=new float[n][];double[] area=new double[n];
        for(int i=0;i<n;i++){float[] p=loops.get(i);float a=Float.POSITIVE_INFINITY,b=a,c=Float.NEGATIVE_INFINITY,d=c;
            for(int k=0;k+1<p.length;k+=2){a=Math.min(a,p[k]);c=Math.max(c,p[k]);b=Math.min(b,p[k+1]);d=Math.max(d,p[k+1]);}
            box[i]=new float[]{a,b,c,d};area[i]=Math.abs(StyleKit.signedArea(p));}
        Integer[] order=new Integer[n];for(int i=0;i<n;i++)order[i]=i;
        java.util.Arrays.sort(order,(x,y)->{int c=Double.compare(area[y],area[x]);return c!=0?c:Integer.compare(x,y);});
        // Parent = smallest larger loop containing this loop's first vertex.
        int[] parent=new int[n];java.util.Arrays.fill(parent,-1);
        for(int oi=0;oi<n;oi++){int i=order[oi];float x=loops.get(i)[0],y=loops.get(i)[1];double best=Double.POSITIVE_INFINITY;
            for(int oj=0;oj<oi;oj++){int j=order[oj];if(area[j]<=area[i]||area[j]>=best)continue;float[] q=box[j];
                if(x<q[0]||x>q[2]||y<q[1]||y>q[3])continue;
                if(insideLoop(loops.get(j),x,y)){best=area[j];parent[i]=j;}}}
        int[] root=new int[n];
        for(int i=0;i<n;i++){int r=i;while(parent[r]>=0)r=parent[r];root[i]=r;}
        boolean[] hit=new boolean[n];
        for(int i=0;i<n;i++){float[] q=box[i];
            for(float[] w:windows)if(q[0]<=w[2]+halo&&q[2]>=w[0]-halo&&q[1]<=w[3]+halo&&q[3]>=w[1]-halo){hit[root[i]]=true;break;}}
        for(int i=0;i<n;i++)(hit[root[i]]?touched:untouched).add(loops.get(i));
    }
    private static boolean insideLoop(float[] p,float x,float y){boolean in=false;int n=p.length/2;
        for(int i=0,j=n-1;i<n;j=i++){float yi=p[2*i+1],yj=p[2*j+1];if((yi>y)!=(yj>y)&&x<p[2*i]+(y-yi)*(p[2*j]-p[2*i])/(yj-yi))in=!in;}return in;}
}

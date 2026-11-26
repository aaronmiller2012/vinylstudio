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
}

package com.halftonestudio;

import java.util.ArrayList;
import java.util.Arrays;

/** Dynamic bounding-volume hierarchy. Leaf IDs stay stable; queries return the
 * exact inclusive AABB intersection set in original contour order. No clearance
 * decision is made here: existing narrow-phase and final preflight still run. */
final class ContourBoundsIndex {
    private final float[][] tree;
    private final int[] leaf, ids;
    private final int base;
    private boolean conservativeFallback;
    ContourBoundsIndex(float[][] bounds){
        int b=1;while(b<bounds.length)b*=2;base=b;
        tree=new float[2*b][];leaf=new int[bounds.length];ids=new int[b];Arrays.fill(ids,-1);
        Integer[] order=new Integer[bounds.length];for(int i=0;i<order.length;i++){CancellationCheckpoint.check();order[i]=i;if(!finite(bounds[i]))conservativeFallback=true;}
        if(conservativeFallback)return;
        // Spatially group nearby loops, keeping original IDs for exact output order.
        Arrays.sort(order,(a,c)->{int x=Float.compare(bounds[a][0]+bounds[a][2],bounds[c][0]+bounds[c][2]);return x!=0?x:Integer.compare(a,c);});
        for(int i=0;i<order.length;i++){int id=order[i];ids[i]=id;leaf[id]=b+i;tree[b+i]=bounds[id].clone();}
        for(int i=b-1;i>0;i--)tree[i]=union(tree[i*2],tree[i*2+1]);
    }
    private static float[] union(float[] a,float[] b){
        if(a==null)return b;if(b==null)return a;
        return new float[]{Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.max(a[2],b[2]),Math.max(a[3],b[3])};
    }
    static boolean overlaps(float[] a,float[] b){return a[0]<=b[2]&&a[2]>=b[0]&&a[1]<=b[3]&&a[3]>=b[1];}
    private static boolean finite(float[] a){return a!=null&&a.length==4&&Float.isFinite(a[0])&&Float.isFinite(a[1])&&Float.isFinite(a[2])&&Float.isFinite(a[3])&&a[0]<=a[2]&&a[1]<=a[3];}
    void update(int id,float[] bounds){if(!finite(bounds))conservativeFallback=true;if(conservativeFallback)return;int p=leaf[id];tree[p]=bounds.clone();while((p/=2)>0)tree[p]=union(tree[p*2],tree[p*2+1]);}
    int[] query(float[] box,int include){
        CancellationCheckpoint.check();
        if(conservativeFallback||!finite(box)){int[] all=new int[leaf.length];for(int i=0;i<all.length;i++)all[i]=i;return all;}
        ArrayList<Integer> out=new ArrayList<>();collect(1,box,out);
        if(include>=0&&!out.contains(include))out.add(include);
        int[] result=new int[out.size()];for(int i=0;i<result.length;i++)result[i]=out.get(i);Arrays.sort(result);return result;
    }
    private void collect(int node,float[] box,ArrayList<Integer> out){
        if(tree[node]==null||!overlaps(tree[node],box))return;
        if(node>=base){out.add(ids[node-base]);return;}
        collect(node*2,box,out);collect(node*2+1,box,out);
    }
}

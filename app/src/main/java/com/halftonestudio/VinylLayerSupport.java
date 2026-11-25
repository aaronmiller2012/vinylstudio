package com.halftonestudio;
/** Selective vinyl underlay. Fill an enclosed opening only when all of its
 * pixels are owned by later-applied opaque vinyl colors. This removes needlessly
 * cut holes while preserving the exact final composite after assembly. */
public final class VinylLayerSupport {
    private VinylLayerSupport(){}
    public static int fillCoveredHoles(boolean[] mask,int[] owner,boolean[] valid,boolean[] higher,int w,int h){
        if(mask==null||owner==null||valid==null||higher==null||mask.length!=(long)w*h||owner.length!=mask.length||valid.length!=mask.length)
            throw new IllegalArgumentException("Invalid support geometry");
        byte[] visited=new byte[mask.length];int[] q=new int[mask.length];int filled=0;
        for(int seed=0;seed<mask.length;seed++)if(!mask[seed]&&visited[seed]==0){
            int head=0,tail=1;q[0]=seed;visited[seed]=1;
            boolean exposed=false,fullyCovered=true;
            while(head<tail){
                int p=q[head++],x=p%w,y=p/w;
                if(!valid[p]||owner[p]<0||owner[p]>=higher.length||!higher[owner[p]])fullyCovered=false;
                if(x==0||y==0||x==w-1||y==h-1||!valid[p])exposed=true;
                if(x>0)tail=visit(p-1,mask,visited,q,tail);
                if(x+1<w)tail=visit(p+1,mask,visited,q,tail);
                if(y>0)tail=visit(p-w,mask,visited,q,tail);
                if(y+1<h)tail=visit(p+w,mask,visited,q,tail);
            }
            if(!exposed&&fullyCovered){for(int i=0;i<tail;i++)mask[q[i]]=true;filled+=tail;}
        }
        return filled;
    }
    private static int visit(int p,boolean[] mask,byte[] seen,int[] q,int t){
        if(!mask[p]&&seen[p]==0){seen[p]=1;q[t++]=p;}return t;
    }
}

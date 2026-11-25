package com.halftonestudio;

/** Pure helpers for deleting a vinyl sheet without confusing the remaining colors. */
public final class VinylLayerDelete {
    private VinylLayerDelete() { }
    public static void remove(int[] array,int index,int active){
        check(array.length,index,active);
        int removed=array[index];
        System.arraycopy(array,index+1,array,index,active-index-1);
        array[active-1]=removed; // parked for later reuse
    }
    public static void remove(float[] array,int index,int active){
        check(array.length,index,active);
        float removed=array[index];
        System.arraycopy(array,index+1,array,index,active-index-1);
        array[active-1]=removed;
    }
    public static void remove(boolean[] array,int index,int active){
        check(array.length,index,active);
        boolean removed=array[index];
        System.arraycopy(array,index+1,array,index,active-index-1);
        array[active-1]=removed;
    }
    public static int[] removeOrder(int[] physicalOrder,int removed,int active){
        check(physicalOrder.length,removed,active);
        int[] result=physicalOrder.clone();int used=0;
        boolean[] seen=new boolean[active];
        for(int i=0;i<active;i++){
            int value=physicalOrder[i];
            if(value<0||value>=active||value==removed)continue;
            int adjusted=value>removed?value-1:value;
            if(!seen[adjusted]){result[used++]=adjusted;seen[adjusted]=true;}
        }
        for(int i=0;i<active-1;i++)if(!seen[i])result[used++]=i;
        for(int i=active-1;i<result.length;i++)result[i]=i;
        return result;
    }
    private static void check(int length,int index,int active){
        if(active<2||active>length||index<0||index>=active)
            throw new IllegalArgumentException("Invalid physical layer index");
    }
}

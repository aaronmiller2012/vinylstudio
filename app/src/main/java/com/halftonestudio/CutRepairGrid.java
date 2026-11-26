package com.halftonestudio;

/** Physical sampling plan: never trade cut-width fidelity for a memory budget. */
final class CutRepairGrid {
    final int width,height;
    final float pitch;
    private CutRepairGrid(int w,int h,float p){width=w;height=h;pitch=p;}
    /** Largest odd footprint that does not exceed the physical diameter. */
    static int openingRadius(float diameter,float pitch){
        if(!(Float.isFinite(diameter)&&Float.isFinite(pitch)&&diameter>0&&pitch>0))
            throw new IllegalArgumentException("Invalid opening radius");
        return Math.max(0,(int)Math.floor((diameter/(double)pitch-1.0)/2.0));
    }
    static CutRepairGrid plan(float spanX,float spanY,float min,float gap,long budget){
        if(!(Float.isFinite(spanX)&&Float.isFinite(spanY)&&Float.isFinite(min)&&Float.isFinite(gap)
                &&spanX>0&&spanY>0&&min>0&&gap>0&&budget>0))
            throw new IllegalArgumentException("Invalid repair grid dimensions");
        // Seven samples across the vinyl minimum and three across the waste floor.
        double target=Math.min((min+.02)/7.0,(gap+.02)/3.0);
        float pitch=(float)target;
        if(pitch>target)pitch=Math.nextDown(pitch);
        if(!(pitch>0))return null;
        double w=Math.ceil(spanX/(double)pitch)+2,h=Math.ceil(spanY/(double)pitch)+2;
        if(w>Integer.MAX_VALUE||h>Integer.MAX_VALUE||w*h>Math.min(budget,Integer.MAX_VALUE))return null;
        return new CutRepairGrid((int)w,(int)h,pitch);
    }
}

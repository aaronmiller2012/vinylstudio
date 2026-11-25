package com.halftonestudio;

/** Export-facing counts per physical color slot. Registration marks are excluded.
 * Each closed loop (including a hole) requires a blade traversal. Open polylines
 * are pen/engrave paths, NOT vinyl cuts. Never count the containing SVG element.
 */
public final class StyleLayerOperations {
    private StyleLayerOperations() {}
    public static final class Counts {
        public final int bladeContours, penPaths;
        Counts(int bladeContours, int penPaths) {
            this.bladeContours = bladeContours; this.penPaths = penPaths;
        }
    }
    public static Counts[] bySlot(StyleKit.Result result,int style,int variant,int requested) {
        int slots=NewStyleColorSlots.slotCount(style,variant,requested);
        int[] cuts=new int[slots],pens=new int[slots];
        if(result!=null)for(int i=0;i<result.layers.size();i++) {
            if(Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException("Layer count cancelled");
            StyleKit.Layer layer=result.layers.get(i);
            int slot=NewStyleColorSlots.slot(style,layer.name,i);
            if(slot<0||slot>=slots)throw new IllegalStateException("Export layer has no physical swatch slot: "+layer.name);
            for(float[] loop:layer.loops)if(loop!=null&&loop.length>=6)cuts[slot]++;
            for(float[] line:layer.lines)if(line!=null&&line.length>=4)pens[slot]++;
        }
        Counts[] out=new Counts[slots];
        for(int i=0;i<slots;i++)out[i]=new Counts(cuts[i],pens[i]);
        return out;
    }
    public static int totalCuts(Counts[] totals){int count=0;for(Counts x:totals)count+=x.bladeContours;return count;}
    public static int totalPenPaths(Counts[] totals){int count=0;for(Counts x:totals)count+=x.penPaths;return count;}
}

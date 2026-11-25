package com.halftonestudio;

/** Per-color-slot blade and pen accounting must match export geometry. */
public final class StyleLayerOperationsTest {
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
    private static float[] square(float x){return new float[]{x,0,x+2,0,x+2,2,x,2};}
    private static float[] line(){return new float[]{0,0,5,5,9,5};}
    public static void main(String[] args){
        for(int style=0;style<8;style++){
            for(int count=2;count<=8;count++){
                StyleKit.Result r=new StyleKit.Result(100,80);
                StyleKit.Layer a=new StyleKit.Layer(style==VinylStyleSuite.GLASS?"lead":"unit-1",0xff332211);
                a.loops.add(square(1));a.loops.add(square(5));a.lines.add(line());r.layers.add(a);
                StyleLayerOperations.Counts[] ops=StyleLayerOperations.bySlot(r,style,0,count);
                check(ops.length==NewStyleColorSlots.slotCount(style,0,count),"slot count "+style);
                check(ops[0].bladeContours==2&&ops[0].penPaths==1,"first slot accounting "+style);
                check(StyleLayerOperations.totalCuts(ops)==2,"summed cuts");
                check(StyleLayerOperations.totalPenPaths(ops)==1,"summed pens");
            }
        }
        // Correct when an engine omits an empty color, instead of shifting the next slot.
        StyleKit.Result topo=new StyleKit.Result(100,100);
        StyleKit.Layer l=new StyleKit.Layer("topo-4",0xff008877);l.loops.add(square(4));topo.layers.add(l);
        StyleLayerOperations.Counts[] counts=StyleLayerOperations.bySlot(topo,VinylStyleSuite.TOPOGRAPHIC,0,6);
        for(int i=0;i<counts.length;i++)check(counts[i].bladeContours==(i==3?1:0),"topographic slot identity");
        StyleKit.Result glass=new StyleKit.Result(100,100);
        StyleKit.Layer lead=new StyleKit.Layer("lead",0xff223344);lead.loops.add(square(0));glass.layers.add(lead);
        StyleKit.Layer panes=new StyleKit.Layer("glass-2",0xff556677);panes.loops.add(square(8));glass.layers.add(panes);
        counts=StyleLayerOperations.bySlot(glass,VinylStyleSuite.GLASS,0,5);
        check(counts[0].bladeContours==1&&counts[2].bladeContours==1,"glass physical lead anchor");
        StyleKit.Result tsp=new StyleKit.Result(100,100);
        StyleKit.Layer pen=new StyleKit.Layer("tsp-line",0xff000000);pen.lines.add(line());tsp.layers.add(pen);
        counts=StyleLayerOperations.bySlot(tsp,VinylStyleSuite.TSP,0,6);
        check(counts[0].bladeContours==0&&counts[0].penPaths==1,"TSP pen cannot be counted as blade cut");
        StyleKit.Result lino=new StyleKit.Result(100,100);
        StyleKit.Layer cross=new StyleKit.Layer("crosshatch",0xff000000);cross.loops.add(square(2));lino.layers.add(cross);
        counts=StyleLayerOperations.bySlot(lino,VinylStyleSuite.LINOCUT,1,6);
        check(counts.length==2&&counts[1].bladeContours==1,"linocut cross hatch layer");
        System.out.println("PASS: all 8 styles, 2-8 slots, hole loops, pen paths, lead, empty slots, topo, linocut");
    }
}

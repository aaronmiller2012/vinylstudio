package com.halftonestudio;

public final class V3MilestoneA3B1HostTest {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static void bad(Runnable r,String why){boolean failed=false;
        try{r.run();}catch(IllegalArgumentException expected){failed=true;}
        check(failed,why);
    }
    public static void main(String[] args){
        // Regression: never scale two physically different layer artboards
        // independently to make a misleading assembly PDF.
        PhysicalSheetParity.requireSameBoard(299.4f,223.2f,299.42f,223.18f);
        bad(()->PhysicalSheetParity.requireSameBoard(299.4f,223.2f,299.5f,223.2f),"Shifted assembly sheet passed");
        bad(()->PhysicalSheetParity.requireSameBoard(299.4f,223.2f,299.4f,223.26f),"Wrong sheet height passed");
        bad(()->PhysicalSheetParity.requireSameBoard(Float.NaN,223.2f,299.4f,223.2f),"NaN sheet passed");
        bad(()->PhysicalSheetParity.requireFitsArtwork(200f,250f,210f,240f),"Oversized artwork passed");
        // The artwork *excludes* registration padding while the cut sheet includes it.
        PhysicalSheetParity.requireFitsArtwork(299.4f,223.2f,279.4f,203.2f);
        AssemblyGuidePlan plan=new AssemblyGuidePlan.Builder("Portrait","Framed Square",279.4f,203.2f,0xfffafafa)
            .registration(true,"Square",3f)
            .addLayer(new AssemblyGuidePlan.Layer(1,"Base",0xff000000,34,true))
            .addLayer(new AssemblyGuidePlan.Layer(2,"Yellow",0xffffff00,178,true))
            .addLayer(new AssemblyGuidePlan.Layer(3,"Not exported",0xff00ff00,33,false))
            .addLayer(new AssemblyGuidePlan.Layer(4,"Engrave",0xff555555,27,true,AssemblyGuidePlan.Kind.PEN_OR_ENGRAVE,""))
            .addLayer(new AssemblyGuidePlan.Layer(5,"Yellow second sheet",0xffffff00,21,true)).build();
        AssemblyMaterialPlan material=AssemblyMaterialPlan.from(plan,299.4f,223.2f);
        check(material.vinylSheets==3,"Counts include pen or excluded sheet");
        check(material.sheetsByArgb.get(0xffffff00)==2,"Repeated color omitted");
        check(material.sheetsByArgb.size()==2,"Unexpected swatches");
        check(Math.abs(material.totalBlankAreaSqCm-(double)299.4*223.2*3/100)<.0001,"Area units wrong");
        check(material.summary().contains("3 full-size")&&material.disclaimer().contains("not vinyl coverage"),"Misleading production estimate");
        boolean immutable=false;try{material.sheetsByArgb.put(0xff000000,99);}
        catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable,"Exposed mutable material counts");
        AssemblyGuidePlan pen=new AssemblyGuidePlan.Builder("Draw","Single Line",100,200,0xffffffff)
            .addLayer(new AssemblyGuidePlan.Layer(1,"Pen",0xff000000,1,true,AssemblyGuidePlan.Kind.PEN_OR_ENGRAVE,"")).build();
        check(AssemblyMaterialPlan.from(pen,100f,200f).vinylSheets==0,"Pen path charged as vinyl");
        System.out.println("PASS A3 physical-board parity and B1 honest material-sheet planning");
    }
}

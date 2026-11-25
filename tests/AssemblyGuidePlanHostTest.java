package com.halftonestudio;

public final class AssemblyGuidePlanHostTest {
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args){
        AssemblyGuidePlan plan=new AssemblyGuidePlan.Builder("Portrait","CMYK Line",279.4f,355.6f,0xffffffff)
            .registration(true,"Square",6f)
            .addLayer(new AssemblyGuidePlan.Layer(1,"Yellow",0xffffff00,184,true))
            .addLayer(new AssemblyGuidePlan.Layer(2,"Black",0xff000000,221,true,
                AssemblyGuidePlan.Kind.VINYL,"Top detail"))
            .layerMappingComplete(true).build();
        check(plan.layers.size()==2,"Layer order lost");
        check(plan.layers.get(0).physicalIndex==1,"Physical index changed");
        check(plan.previewText().contains("11.00 × 14.00 in"),"Physical size missing");
        check(plan.previewText().contains("2. Black · #000000 · 221 cut contours"),"Edited layer metadata missing");
        check(plan.vinylLayerCount()==2,"Vinyl operation count incorrect");
        check(!plan.previewText().contains("incomplete"),"Complete plan reported as incomplete");
        AssemblyGuidePlan reference=new AssemblyGuidePlan.Builder("Portrait","Fingerprint",203.2f,254f,0xffffffff)
            .registration(false,"None",0f).referenceOnly(true)
            .addLayer(new AssemblyGuidePlan.Layer(1,"Portrait vinyl",0xff000000,55,true)).build();
        check(reference.referenceOnly&&!reference.registration,"Reference guide must omit all marks");
        check(reference.previewText().contains("REFERENCE ONLY"),"Reference safety notice missing");
        boolean rejected=false;
        try{new AssemblyGuidePlan.Layer(0,"Bad",0,0,true);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Invalid physical index accepted");
        rejected=false;
        try{
            new AssemblyGuidePlan.Builder("Bad","Order",100,100,0xffffffff)
                .addLayer(new AssemblyGuidePlan.Layer(2,"Second",0,1,true))
                .addLayer(new AssemblyGuidePlan.Layer(1,"First",0,1,true));
        }catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Out-of-order physical layers accepted");
        System.out.println("PASS assembly guide plan contract");
    }
}

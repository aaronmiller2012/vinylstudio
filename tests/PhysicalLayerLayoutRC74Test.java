package com.halftonestudio;
public final class PhysicalLayerLayoutRC74Test{
    public static void main(String[] args){
        for(int detail=2;detail<=8;detail++){
            assert PhysicalLayerLayout.physicalCount(detail,true)==detail+1;
            assert PhysicalLayerLayout.physicalCount(detail,false)==detail;
            assert PhysicalLayerLayout.detailColorsForPhysical(detail+1,true)==detail;
            for(int i=0;i<detail;i++){
                assert PhysicalLayerLayout.physicalNumber(i,true)==i+2;
                assert PhysicalLayerLayout.physicalNumber(i,false)==i+1;
            }
        }
        assert PhysicalLayerLayout.detailColorsForPhysical(1,true)==0;
        boolean rejected=false;try{PhysicalLayerLayout.detailColorsForPhysical(2,true);}catch(IllegalArgumentException ex){rejected=true;}
        assert rejected;System.out.println("PASS RC74 physical numbering, roundtrip and unsupported single-detail guard");
    }
}

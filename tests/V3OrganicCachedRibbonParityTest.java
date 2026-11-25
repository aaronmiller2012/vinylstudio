package com.halftonestudio;
public final class V3OrganicCachedRibbonParityTest {
    public static void main(String[] args){
        int cases=0;
        for(float pitch:new float[]{1.9f,2.8f,4.1f,7.5f}){
            OrganicFlowPortraitEngine.RibbonField field=
                new OrganicFlowPortraitEngine.RibbonField(pitch,101.6f,118.0f);
            for(int iy=0;iy<119;iy++)for(int ix=0;ix<117;ix++){
                float x=(ix+.5f)*(101.6f/117),y=(iy+.5f)*(118f/119);
                float gx=(float)Math.sin(ix*.4)*.08f,gy=(float)Math.cos(iy*.33)*.08f;
                float edge=(ix+iy)%23/22f,flow=.3f+((ix*31+iy*13)%210)/100f;
                boolean old=OrganicFlowPortraitEngine.ribbon(x,y,pitch,.24f,.8f,.5f,flow,gx,gy,edge);
                boolean now=field.ribbon(x,y,pitch,.24f,.8f,.5f,flow,gx,gy,edge);
                if(old!=now)throw new AssertionError("Cached coil changed shape at "+x+","+y+" pitch "+pitch);
                cases++;
            }
        }
        System.out.println("PASS cached paper-quill ribbon parity "+cases+" pixel evaluations");
    }
}

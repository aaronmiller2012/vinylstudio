package com.halftonestudio;
public final class RenderDraftPolicyHostTest {
    static void require(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    public static void main(String[] args){
        int[][] samples={{2000,1000},{1200,1600},{640,480},{60,80},{3000,4000}};
        for(int[] image:samples){
            int[] size=RenderDraftPolicy.dimensions(image[0],image[1]);
            int[] again=RenderDraftPolicy.dimensions(image[0],image[1]);
            require(size[0]==again[0]&&size[1]==again[1],"Non-deterministic dimension policy");
            require(size[0]<=image[0]&&size[1]<=image[1],"Upsampled draft");
            require(Math.max(size[0],size[1])<=RenderDraftPolicy.MAX_SIDE_PX,"Draft limit exceeded");
            require(Math.abs((double)size[0]/size[1]-(double)image[0]/image[1])<.04,"Aspect ratio changed");
        }
        require(RenderDraftPolicy.dimensions(2000,1000)[0]==480,"Large source not capped");
        require(RenderDraftPolicy.dimensions(400,200)[0]==200,"Draft must use quarter pixel area");
        require(RenderDraftPolicy.dimensions(64,60)[0]==64,"Tiny image unnecessarily sampled");
        require(RenderDraftPolicy.DEBOUNCE_MS==60,"Drag latency changed");
        require(RenderDraftPolicy.sampleStepMm(.45f)==.7f,"Draft sample density changed");
        require(RenderDraftPolicy.sampleStepMm(.9f)==.9f,"Draft must not increase trace quality");
        require(!RenderDraftPolicy.supportsSpecialtyDraft(true,-1),"Maze should not build expensive drafts");
        require(!RenderDraftPolicy.supportsSpecialtyDraft(false,VinylStyleSuite.TSP),"TSP should not build expensive drafts");
        require(RenderDraftPolicy.supportsSpecialtyDraft(false,VinylStyleSuite.BANKNOTE),"Banknote draft unavailable");
        System.out.println("PASS bounded draft, 60ms standard debounce, physical step only and expensive-style fallback");
    }
}

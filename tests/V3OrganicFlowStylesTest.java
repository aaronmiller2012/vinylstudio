package com.halftonestudio;

import java.util.Arrays;

/** Pure JVM smoke/adversarial tests for the three photo-directed organic styles. */
public final class V3OrganicFlowStylesTest {
    private static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static int[] sample(int w,int h,boolean[] mask){
        int[] image=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float u=(x-w*.5f)/(w*.44f),v=(y-h*.5f)/(h*.48f);
            boolean face=u*u+v*v<1f;
            boolean hole=(x-w*.53f)*(x-w*.53f)+(y-h*.47f)*(y-h*.47f)<w*w*.0024f;
            mask[y*w+x]=face&&!hole;
            int val=face?(int)(42+185*(.52f+.23f*u-.19f*v)):
                245;
            // Two eyes and a distinct dark mouth. Extremely fine feature boundaries.
            if(face && ((Math.abs(y-h*.37f)<h*.022f&&
                (Math.abs(x-w*.37f)<w*.052f||Math.abs(x-w*.63f)<w*.052f))||
                (Math.abs(y-h*.69f)<h*.018f&&Math.abs(x-w*.5f)<w*.14f)))val=12;
            val=StyleKit.clampI(val,0,255);
            image[y*w+x]=0xff000000|(val<<16)|((val+Math.min(24,255-val))<<8)|val;
        }
        return image;
    }
    private static boolean same(StyleKit.Result a,StyleKit.Result b){
        if(a.layers.size()!=b.layers.size())return false;
        for(int l=0;l<a.layers.size();l++){
            StyleKit.Layer x=a.layers.get(l),y=b.layers.get(l);
            if(x.loops.size()!=y.loops.size())return false;
            for(int i=0;i<x.loops.size();i++)if(!Arrays.equals(x.loops.get(i),y.loops.get(i)))return false;
        }return true;
    }
    private static void run(int mode,boolean maskOn){
        int w=85,h=103;boolean[] mask=new boolean[w*h];int[] pixels=sample(w,h,mask);
        OrganicFlowPortraitEngine.Options o=new OrganicFlowPortraitEngine.Options();
        o.mode=mode;o.detail=91;o.layers=6;o.widthMm=101.6f;
        o.minCutMm=.7f;o.gapMm=.5f;o.flow=1.05f;
        o.palette=new int[]{0xff182128,0xff454060,0xff627687,0xffa48181,
            0xffc2a793,0xffe9dacc};
        if(maskOn)o.allowed=mask;
        StyleKit.Result a=OrganicFlowPortraitEngine.generate(pixels,w,h,o);
        ok(a.layers.size()>=3,"Insufficient populated layers for mode "+mode);
        ok(a.layers.size()<=6,"Too many physical sheets");
        ok(a.totalPoints()>180,"No portrait detail: "+mode);
        boolean foundBase=false;
        for(StyleKit.Layer layer:a.layers){
            if(layer.name.endsWith("-1"))foundBase=true;
            ok(layer.lines.isEmpty(),"Unsafe pen paths for vinyl style "+mode);
            for(float[] loop:layer.loops){
                ok(loop.length>=6&&(loop.length&1)==0,"Malformed loop");
                for(int j=0;j<loop.length;j+=2){
                    ok(Float.isFinite(loop[j])&&Float.isFinite(loop[j+1]),"nonfinite");
                    ok(loop[j]>=-.001&&loop[j]<=a.widthMm+.001&&
                       loop[j+1]>=-.001&&loop[j+1]<=a.heightMm+.001,
                       "Outside physical artboard");
                }
            }
        }
        ok(foundBase,"No structural Snip base");
        StyleKit.Result b=OrganicFlowPortraitEngine.generate(pixels,w,h,o);
        ok(same(a,b),"Output not deterministic for mode "+mode);
        System.out.println("PASS "+VinylStyleSuite.NAMES[VinylStyleSuite.QUILLING_RIBBONS+mode]+
            " · "+a.layers.size()+" sheets / "+a.totalPoints()+" points");
    }
    public static void main(String[] args){
        for(int mode=0;mode<3;mode++)run(mode,true);
        // Independent alpha/snip boundary cases and minimum two-color output.
        for(int mode=0;mode<3;mode++)run(mode,false);
        boolean rejected=false;
        try{OrganicFlowPortraitEngine.Options o=new OrganicFlowPortraitEngine.Options();
            o.flow=Float.NaN;o.validate();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"Non-finite flow accepted");
        for(int id=VinylStyleSuite.QUILLING_RIBBONS;id<=VinylStyleSuite.QUILLED_MARBLING;id++){
            ok(NewStyleVariantPolicy.count(id)==1,"Style not selectable");
            ok(NewStyleLayerPolicy.isStructuralLayer(id,0),"L1 must be structural");
            ok(NewStyleColorSlots.multi(id),"Overlay palette missing");
            ok(VinylStyleSuite.organicCutGeometry(id),"Style not using shared conditioner");
        }
        // The original Organic Flow release had 101 identities. Overprint Cells
        // added four independent picker thumbnails. Two retired
        // entries are now excluded; cardinality still matches remaining identities.
        // The Prismatic ribbon milestone appended five more (Contour Flow, Twill,
        // Silk, Braided Flow, Chevron).
        int expectedThumbnailCount = StudioThumbnailRegistry.TOP_LEVEL_COUNT
                + StudioThumbnailRegistry.TESSELLATION_COUNT;
        ok(expectedThumbnailCount == 109,
                "Unexpected thumbnail identity baseline: " + expectedThumbnailCount);
        ok(StudioThumbnailRegistry.allKeys().size() == expectedThumbnailCount,
                "Missing or duplicate thumbnail identities");
        for (String key : new String[]{
                "multilayer.layered_cells", "multilayer.offset_cells",
                "multilayer.overprint_orbits", "multilayer.overprint_lozenge",
                "multilayer.overprint_diamonds", "multilayer.overprint_mixed"}) {
            ok(StudioThumbnailRegistry.isKnown(key), "Missing cell thumbnail identity: " + key);
        }
        String[] keys={"multilayer.quilling_ribbons","multilayer.marbled_ink","multilayer.quilled_marbling"};
        for(int i=0;i<keys.length;i++){
            int id=VinylStyleSuite.QUILLING_RIBBONS+i;
            ok(StudioThumbnailRegistry.newStyle(id).equals(keys[i]),"Wrong OrganicFlow thumbnail for ID "+id);
            ok(StudioThumbnailRegistry.isKnown(keys[i]),"OrganicFlow thumbnail missing: "+keys[i]);
        }
        System.out.println("PASS all organic-flow style invariants");
    }
}

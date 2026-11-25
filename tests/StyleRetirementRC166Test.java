package com.halftonestudio;

/** Executable retirement checks: no ID reuse, no retired generator or picker thumbnails. */
public final class StyleRetirementRC166Test {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void illegal(Runnable action,String name){
        try{action.run();throw new AssertionError("Retired style still accessible: "+name);}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] ignored){
        for(int id:new int[]{VinylStyleSuite.LEAF_VEIN,VinylStyleSuite.LACE}){
            require(VinylStyleSuite.isRetiredStyleId(id),"Style is not retired: "+id);
            require(VinylStyleSuite.restoredStyleId(id)==-1,"Saved style ID reused: "+id);
            require(!NewStyleVariantPolicy.selectable(id),"Retired style selectable: "+id);
            final VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=id;c.colorCount=9;
            illegal(()->VinylStyleSuite.generate(new int[]{0xff997755},1,1,c),"engine "+id);
            illegal(()->StudioThumbnailRegistry.newStyle(id),"thumbnail "+id);
        }
        require(!StudioThumbnailRegistry.isKnown("multilayer.leaf_vein"),"Old leaf thumbnail active");
        require(!StudioThumbnailRegistry.isKnown("multilayer.lace"),"Old lace thumbnail active");
        require(VinylStyleSuite.restoredStyleId(VinylStyleSuite.FISHNET)==VinylStyleSuite.FISHNET,"Fishnet removed");
        require(VinylStyleSuite.restoredStyleId(VinylStyleSuite.CROCHET)==VinylStyleSuite.CROCHET,"Crochet removed");
        require(TessellationMotifEngine.PATTERNS.length==37,"Extended variant table incomplete");
        require(TessellationMotifEngine.ACTIVE_PATTERN_COUNT==28,"Expected 28 active tessellations");
        for(int i=0;i<37;i++){
            final int pattern=i;
            if(i<18){
                require(TessellationMotifEngine.selectablePattern(i),"Active variant retired "+i);
                require(NewStyleVariantPolicy.restore(VinylStyleSuite.TESSELLATION_MOTIF,i)==i,"Variant renumbered "+i);
                require(StudioThumbnailRegistry.tessellationVariant(StudioThumbnailRegistry.tessellationKey(i))==i,"Thumbnail changed "+i);
            }else if(TessellationMotifEngine.retiredPattern(i)){
                require(!TessellationMotifEngine.selectablePattern(i),"Retired variant selectable "+i);
                require(NewStyleVariantPolicy.restore(VinylStyleSuite.TESSELLATION_MOTIF,i)==TessellationMotifEngine.TRIHEX,"Unsafe restore "+i);
                illegal(()->StudioThumbnailRegistry.tessellationKey(pattern),"tessellation thumbnail "+i);
                TessellationMotifEngine.Options bad=new TessellationMotifEngine.Options();bad.pattern=i;
                illegal(bad::validate,"tessellation engine "+i);
            }else{
                require(TessellationMotifEngine.selectablePattern(i),"New variant missing "+i);
                require(NewStyleVariantPolicy.restore(VinylStyleSuite.TESSELLATION_MOTIF,i)==i,"New saved variant missing "+i);
                require(StudioThumbnailRegistry.tessellationVariant(StudioThumbnailRegistry.tessellationKey(i))==i,"New thumbnail missing "+i);
            }
        }
        int next=0;
        for(int i=0;i<60;i++){
            next=NewStyleVariantPolicy.next(VinylStyleSuite.TESSELLATION_MOTIF,next);
            require(TessellationMotifEngine.selectablePattern(next),"Cycle reached retired variant");
        }
        require(StudioThumbnailRegistry.allKeys().size()==StudioThumbnailRegistry.TOP_LEVEL_COUNT+StudioThumbnailRegistry.TESSELLATION_COUNT,"Unexpected picker thumbnail count");
        System.out.println("PASS RC166: 2 retired craft IDs, 9 reserved tessellation variants, safe restoration and current active thumbnails");
    }
}

package com.halftonestudio;

import java.util.HashSet;
/** User-requested permanent picker retirement. No photo or Android runtime needed. */
public final class TessellationBeta8RetirementTest {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        int[] removed={24,27,32,33,36};
        int active=0;
        for(int id=0;id<TessellationMotifEngine.PATTERN_SLOT_COUNT;id++){
            final int variant=id;
            if(TessellationMotifEngine.selectablePattern(id)){
                active++;
                require(NewStyleVariantPolicy.restore(VinylStyleSuite.TESSELLATION_MOTIF,id)==id,"Changed saved ID "+id);
                require(StudioThumbnailRegistry.isKnown(StudioThumbnailRegistry.tessellationKey(id)),"Missing active key "+id);
            }else{
                require(NewStyleVariantPolicy.restore(VinylStyleSuite.TESSELLATION_MOTIF,id)==TessellationMotifEngine.TRIHEX,"Unsafe project restore "+id);
                require(StudioThumbnailRegistry.tessellationVariant(String.format(java.util.Locale.ROOT,"tess.variant.%02d",id))==-1,"Retired thumbnail restored "+id);
                try{StudioThumbnailRegistry.tessellationKey(id);throw new AssertionError("Retired key allowed "+id);}
                catch(IllegalArgumentException expected){}
                TessellationMotifEngine.Options bad=new TessellationMotifEngine.Options();bad.pattern=id;
                try{bad.validate();throw new AssertionError("Retired renderer allowed "+id);}
                catch(IllegalArgumentException expected){}
            }
        }
        require(active==28&&TessellationMotifEngine.ACTIVE_PATTERN_COUNT==28,"Expected 28 active styles");
        require(TessellationMotifEngine.PATTERN_SLOT_COUNT==37&&TessellationMotifEngine.PATTERNS.length==37,"Persistent IDs shifted");
        require(StudioThumbnailRegistry.allKeys().size()==StudioThumbnailRegistry.TOP_LEVEL_COUNT+StudioThumbnailRegistry.TESSELLATION_COUNT,"Thumbnail registry count changed");
        HashSet<Integer> cycled=new HashSet<>();
        int variant=0;
        for(int i=0;i<28;i++){
            variant=NewStyleVariantPolicy.next(VinylStyleSuite.TESSELLATION_MOTIF,variant);
            require(TessellationMotifEngine.selectablePattern(variant),"Cycle visited removed style");
            require(cycled.add(variant),"Cycle duplicated style before wrap");
        }
        require(variant==0&&cycled.size()==28,"Cycle did not visit all active styles exactly once");
        for(int id:removed){
            require(TessellationMotifEngine.retiredPattern(id),"Crossed-out style still active: "+id);
            require(NewStyleVariantPolicy.next(VinylStyleSuite.TESSELLATION_MOTIF,id)!=id,"Cycle stayed on retired ID "+id);
        }
        System.out.println("PASS Beta8: five requested variants retired; 28 selectable; safe project migration, thumbnail and cycle contracts");
    }
}

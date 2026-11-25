package com.halftonestudio;

import java.util.Arrays;
import java.util.List;

public final class StudioProjectGalleryModelTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static StudioProjectLibrary.Entry e(String id,String name,String style,long date){
        return new StudioProjectLibrary.Entry(id,name,style,date);
    }
    public static void main(String[] args){
        StudioProjectLibrary.Entry portrait=e("b","Portrait","CMYK",100);
        StudioProjectLibrary.Entry scene=e("a","Forest","Stencil",300);
        StudioProjectLibrary.Entry second=e("c","portrait two","Line",200);
        List<StudioProjectLibrary.Entry> original=Arrays.asList(portrait,scene,second);
        List<StudioProjectLibrary.Entry> recent=StudioProjectGalleryModel.visible(original,"",StudioProjectGalleryModel.RECENT);
        check(recent.get(0)==scene&&recent.get(2)==portrait,"recent order");
        check(original.get(0)==portrait,"input list must not be mutated");
        List<StudioProjectLibrary.Entry> names=StudioProjectGalleryModel.visible(original,"",StudioProjectGalleryModel.NAME);
        check(names.get(0)==scene&&names.get(1)==portrait,"name order case insensitive");
        List<StudioProjectLibrary.Entry> styles=StudioProjectGalleryModel.visible(original,"",StudioProjectGalleryModel.STYLE);
        check(styles.get(0)==portrait&&styles.get(1)==second,"style order");
        check(StudioProjectGalleryModel.visible(original,"  pOrTrAiT ",0).size()==2,"case insensitive name match");
        check(StudioProjectGalleryModel.visible(original,"  PORTRAIT   CMYK ",0).size()==1,"multiple search terms");
        check(StudioProjectGalleryModel.visible(original,"stencil",0).get(0)==scene,"style match");
        check(StudioProjectGalleryModel.visible(original,"unknown",0).isEmpty(),"no results");
        check(StudioProjectGalleryModel.visible(original,null,0).size()==3,"null query");
        check(StudioProjectGalleryModel.visible(null,"",0).isEmpty(),"null source");
        check(StudioProjectGalleryModel.normalizeSort(-4)==0&&StudioProjectGalleryModel.normalizeSort(99)==0,"bad sort preference safe");
        check(StudioProjectGalleryModel.columnsForWidth(412)==2,"phone columns");
        check(StudioProjectGalleryModel.columnsForWidth(600)==3,"tablet columns");
        check(StudioProjectGalleryModel.columnsForWidth(840)==4,"wide columns");
        System.out.println("PASS RC58 gallery search, sort, immutability and adaptive columns");
    }
}

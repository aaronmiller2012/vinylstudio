package com.halftonestudio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Stateless gallery presentation model. Never modifies underlying project entries. */
final class StudioProjectGalleryModel {
    static final int RECENT=0, NAME=1, STYLE=2;
    static int normalizeSort(int sort){return sort>=RECENT&&sort<=STYLE?sort:RECENT;}
    static String sortLabel(int sort){
        switch(normalizeSort(sort)){
            case NAME:return "Name A–Z";
            case STYLE:return "Style A–Z";
            default:return "Recent";
        }
    }
    static int columnsForWidth(int widthDp){return widthDp>=840?4:widthDp>=600?3:2;}
    static List<StudioProjectLibrary.Entry> visible(List<StudioProjectLibrary.Entry> all,String query,int sort){
        String needle=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        String[] terms=needle.isEmpty()?new String[0]:needle.split("\\s+");
        List<StudioProjectLibrary.Entry> result=new ArrayList<>();
        if(all!=null)for(StudioProjectLibrary.Entry entry:all){
            if(entry==null)continue;
            String haystack=(entry.name+" "+entry.style).toLowerCase(Locale.ROOT);
            boolean matches=true;
            for(String term:terms)if(!haystack.contains(term)){matches=false;break;}
            if(matches)result.add(entry);
        }
        Comparator<StudioProjectLibrary.Entry> newest=Comparator.comparingLong((StudioProjectLibrary.Entry p)->p.modified).reversed()
            .thenComparing(p->p.id);
        switch(normalizeSort(sort)){
            case NAME:result.sort(Comparator.comparing((StudioProjectLibrary.Entry p)->p.name,String.CASE_INSENSITIVE_ORDER).thenComparing(newest));break;
            case STYLE:result.sort(Comparator.comparing((StudioProjectLibrary.Entry p)->p.style,String.CASE_INSENSITIVE_ORDER)
                .thenComparing(p->p.name,String.CASE_INSENSITIVE_ORDER).thenComparing(newest));break;
            default:result.sort(newest);
        }
        return result;
    }
}

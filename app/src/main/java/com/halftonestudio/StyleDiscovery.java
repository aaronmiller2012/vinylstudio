package com.halftonestudio;

import java.util.*;

/** UI-only preferences; never used to select a saved project's renderer. */
final class StyleDiscovery {
    static final int LIMIT=128, RECENT_LIMIT=12;
    private StyleDiscovery() {}
    static List<String> decode(String raw,int limit) {
        LinkedHashSet<String> keys=new LinkedHashSet<>();
        if(raw!=null&&raw.length()<=16384) for(String key:raw.split("\\n")) {
            if(key.matches("[a-z0-9_.]{1,80}")) keys.add(key);
            if(keys.size()>=limit) break;
        }
        return new ArrayList<>(keys);
    }
    static void touch(List<String> keys,String key,int limit){
        keys.remove(key); keys.add(0,key);
        while(keys.size()>limit)keys.remove(keys.size()-1);
    }
    static String normalize(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
    static boolean matches(String text,String query){
        String hay=normalize(text), q=normalize(query);
        for(String word:q.split(" "))if(!hay.contains(word))return false;
        return true;
    }
    static String aliases(String key){
        switch(key){
            case "shape.round":return "circle circles dots halftone";
            case "shape.hexagon":return "hex honeycomb bee cells";
            case "shape.diamond":return "rhombus lozenge";
            case "shape.ellipse":return "oval";
            case "line.straight":case "line.variable_width":return "stripes ribbons halftone";
            case "line.wavy":return "waves curves undulating";
            case "multilayer.tonal":return "portrait silhouette stacked stencil";
            case "multilayer.fish_scales":return "scallop fish scale";
            case "multilayer.stained_glass":return "lead window mosaic";
            case "single.spiral":return "photo spiral swirl coil";
            case "multilayer.weighted_stipple":return "dots stippling portrait";
            case "art.torn_collage":return "paper collage portrait organic";
            case "art.banknote":return "money engraving currency hatching";
            default:return "";
        }
    }
}

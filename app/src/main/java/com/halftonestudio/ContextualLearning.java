package com.halftonestudio;
import java.util.Locale;
final class ContextualLearning {
    private ContextualLearning(){}
    static boolean matches(String query,String body){
        String hay=body.toLowerCase(Locale.ROOT);
        for(String word:query.trim().toLowerCase(Locale.ROOT).split("\\s+"))if(!hay.contains(word))return false;
        return true;
    }
    static String sliderHelp(String label,String low,String high){
        // Build 557: every adjustment slider has its own detailed explanation.
        String detailed=SliderGuide.explain(label,low,high);
        if(detailed!=null)
            return detailed+"\n\nRANGE\n"+low+" to "+high+". Change one setting at a time and wait for the final render.";
        String known=VinylPlainLanguage.help(label);
        return (known==null?"Adjust "+VinylPlainLanguage.label(label)+" and compare the final artwork. The effect depends on the chosen style.":known)
            +"\n\nAvailable range: "+low+" to "+high+". Change one setting at a time and wait for the final render. Check physical cut limits before export.";
    }
}

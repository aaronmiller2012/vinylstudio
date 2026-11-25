package com.halftonestudio;

import java.util.Locale;

/** UI/manifest labels for PHYSICAL vinyl colors, not historical CMYK ink names.
 * Channel identity remains available solely as separation metadata.
 * Android-independent so labels are tested before the NAS APK build.
 */
public final class ExportColorLabels {
    private ExportColorLabels(){}
    public static String hex(int color){
        return String.format(Locale.US,"#%06X",color & 0xFFFFFF);
    }
    public static String channel(int id){
        switch(id){
            case 0:return "C separation";
            case 1:return "M separation";
            case 2:return "Y separation";
            case 3:return "K separation";
            default:return "";
        }
    }
    public static String physicalLabel(int index,int color,int channel){
        String ch=channel(channel);
        return String.format(Locale.US,"L%d · %s%s",index+1,hex(color),
            ch.isEmpty()?"":" ("+ch+")");
    }
    public static String fileName(int index,int color){
        return String.format(Locale.US,"%02d-apply-L%d-%06x.svg",index+1,index+1,color&0xFFFFFF);
    }
    public static String status(boolean enabled,boolean exported){
        if(!enabled)return "NOT EXPORTED — disabled";
        return exported?"":"NOT EXPORTED — matches mounting surface";
    }
}

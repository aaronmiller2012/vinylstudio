package com.halftonestudio;

import java.util.Locale;

/** B5: meaningful, filesystem-safe export names, e.g.
 * {@code VinylStudio-Grandma-Tonal-Stencil-cut-layers.zip}. Pure Java (host-tested). */
final class ExportFileNames {
    private ExportFileNames(){}
    /** Generic placeholders that say nothing about the artwork. */
    static boolean generic(String project){
        if(project==null)return true;
        String p=project.trim().toLowerCase(Locale.ROOT);
        return p.isEmpty()||p.equals("photo project")||p.equals("new project")||p.equals("untitled");
    }
    static String part(String s){
        if(s==null)return "";
        String clean=s.trim().replaceAll("[^A-Za-z0-9._ -]","").replaceAll("[ ._-]+","-").replaceAll("^-+|-+$","");
        return clean.length()>48?clean.substring(0,48).replaceAll("-+$",""):clean;
    }
    /** {@code VinylStudio-[project-]style-cut-layers.zip}; never empty, never duplicates a style-named project. */
    static String cutZip(String project,String style){
        String st=part(style),pr=generic(project)?"":part(project);
        StringBuilder b=new StringBuilder("VinylStudio");
        if(!pr.isEmpty())b.append('-').append(pr);
        if(!st.isEmpty()&&!st.equalsIgnoreCase(pr))b.append('-').append(st);
        return b.append("-cut-layers.zip").toString();
    }
}

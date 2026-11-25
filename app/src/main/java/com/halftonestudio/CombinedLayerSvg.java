package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build 556: one multi-color SVG holding every verified cut sheet of an export, so a
 * cutter app such as Cricut Design Space needs ONE upload instead of one per color.
 *
 * <p>Design Space splits an uploaded SVG into layers by group and fill color and, at
 * Make, puts each color on its own mat. Each sheet becomes one {@code <g>} named after
 * its file, keeping its own fill colors and its own copy of the registration marks, so
 * every mat still carries marks in that layer's color.
 *
 * <p>Only sheets that already passed {@link CutSheetZipWriter#put} are merged, and that
 * writer has proven they share one physical artboard and coordinate frame. The geometry
 * is copied byte for byte; nothing is re-traced, re-scaled or simplified.
 */
final class CombinedLayerSvg {
    static final String FILENAME="00-ALL-LAYERS-one-upload.svg";
    static final String SKIPPED_NOTE="00-ALL-LAYERS-not-included.txt";
    private static final Pattern ROOT_OPEN=Pattern.compile("<svg\\b[^>]*>",Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE=Pattern.compile("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(\"[^\"]*\"|'[^']*')");
    private static final Pattern ID=Pattern.compile("(\\sid\\s*=\\s*)([\"'])([^\"']*)\\2");
    /** Root attributes that describe the document frame; everything else (fill, stroke,
     *  transform, ...) is inherited by the content and moves onto the sheet's group. */
    private static final java.util.Set<String> FRAME_ATTRIBUTES=new java.util.HashSet<>(java.util.Arrays.asList(
        "xmlns","width","height","viewbox","preserveaspectratio","version","x","y","id","baseprofile"));

    private CombinedLayerSvg(){}

    static final class Sheet {
        final String name, svg;
        Sheet(String name,String svg){this.name=name;this.svg=svg;}
    }

    /** Merges sheets in order. The first sheet's root frame is used for the document. */
    static String merge(List<Sheet> sheets){
        if(sheets==null||sheets.isEmpty())throw new IllegalArgumentException("No sheets to combine");
        StringBuilder out=new StringBuilder();
        String frame=null;
        List<String> usedIds=new ArrayList<>();
        int index=0;
        for(Sheet sheet:sheets){
            index++;
            Matcher root=ROOT_OPEN.matcher(sheet.svg);
            if(!root.find())throw new IllegalArgumentException("Missing SVG root: "+sheet.name);
            int close=sheet.svg.lastIndexOf("</svg>");
            if(close<root.end())throw new IllegalArgumentException("Unterminated SVG: "+sheet.name);
            StringBuilder inherited=new StringBuilder();
            StringBuilder frameTag=new StringBuilder("<svg");
            Matcher a=ATTRIBUTE.matcher(root.group());
            while(a.find()){
                String key=a.group(1),lower=key.toLowerCase(Locale.ROOT);
                boolean frameAttr=FRAME_ATTRIBUTES.contains(lower)||lower.startsWith("xmlns:");
                if(frameAttr){if(!lower.equals("id"))frameTag.append(' ').append(key).append('=').append(a.group(2));}
                else inherited.append(' ').append(key).append('=').append(a.group(2));
            }
            frameTag.append('>');
            if(frame==null){
                frame=frameTag.toString();
                out.append(frame).append('\n');
            }
            String group=uniqueId(groupId(sheet.name,index),usedIds);
            String body=prefixIds(sheet.svg.substring(root.end(),close),"L"+index+"-");
            out.append("<g id=\"").append(group).append('"').append(inherited).append(">\n")
               .append(body.trim()).append("\n</g>\n");
        }
        return out.append("</svg>\n").toString();
    }

    /** An XML id built from the sheet file name; must start with a letter. */
    static String groupId(String fileName,int index){
        String base=fileName==null?"":fileName.replaceFirst("(?i)\\.svg$","").replaceAll("[^A-Za-z0-9_-]","-");
        if(base.isEmpty())base="layer-"+index;
        return Character.isLetter(base.charAt(0))?base:"L"+base;
    }

    private static String uniqueId(String id,List<String> used){
        String candidate=id;int n=2;
        while(used.contains(candidate))candidate=id+"-"+(n++);
        used.add(candidate);return candidate;
    }

    /** Ids inside different sheets may collide once merged; keep them unique per sheet. */
    private static String prefixIds(String body,String prefix){
        Matcher m=ID.matcher(body);
        StringBuffer sb=new StringBuffer();
        while(m.find())m.appendReplacement(sb,Matcher.quoteReplacement(m.group(1)+m.group(2)+prefix+m.group(3)+m.group(2)));
        m.appendTail(sb);
        return sb.toString();
    }
}

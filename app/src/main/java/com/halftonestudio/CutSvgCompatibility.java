package com.halftonestudio;

import java.util.Locale;

/** Deterministic, cheap preflight for Cricut Design Space SVG uploads.
 * Runs on final exported strings, not draft previews. This is a compatibility
 * check, not a mathematical self-intersection or minimum-width proof. */
public final class CutSvgCompatibility {
    public static final int MAX_ELEMENTS = 5000;
    private CutSvgCompatibility() {}

    public static int verify(String svg) {
        return verify(svg,OutputDeviceProfile.CRICUT);
    }
    /** Device-specific path budgets apply only to the target cutter. Unsafe
     * SVG elements remain forbidden for every vector handoff. */
    public static int verify(String svg,int deviceId) {
        if(svg==null||!svg.contains("<svg"))
            throw new IllegalArgumentException("Missing SVG document");
        String lower=svg.toLowerCase(Locale.ROOT);
        String[] unsupported={"<clippath", "<mask", "<pattern", "<lineargradient", "<radialgradient",
            "<image", "<foreignobject", "<filter", "<text", "<use", "<style", "<symbol", "<script", "<!doctype", "<!entity"};
        for(String tag:unsupported)if(elementCount(lower,tag)>0)
            throw new IllegalArgumentException("Unsupported cut-SVG element: "+tag.substring(1));
        if(java.util.regex.Pattern.compile("(?<![a-z0-9_:-])(?:xlink:)?href\\s*=").matcher(lower).find())
            throw new IllegalArgumentException("External SVG references are unsupported");
        int count=0;
        for(String tag:new String[]{"<path", "<circle", "<ellipse", "<rect", "<polygon", "<polyline", "<line"})
            count+=elementCount(lower,tag);
        if(count==0)throw new IllegalArgumentException("SVG has no cut or drawing paths");
        if(deviceId==OutputDeviceProfile.CRICUT&&count>MAX_ELEMENTS)
            throw new IllegalArgumentException("SVG contains "+count+" shapes; Cricut supports at most "+MAX_ELEMENTS+" per upload. Reduce detail or export fewer shapes per file.");
        return count;
    }
    /** Count tag-name boundaries only; <pathway> is not a path element. */
    private static int elementCount(String lower,String tag){
        int count=0,p=0;
        while((p=lower.indexOf(tag,p))>=0){
            int end=p+tag.length();
            if(end<lower.length()){
                char c=lower.charAt(end);
                if(Character.isWhitespace(c)||c=='>'||c=='/')count++;
            }
            p=end;
        }
        return count;
    }
}

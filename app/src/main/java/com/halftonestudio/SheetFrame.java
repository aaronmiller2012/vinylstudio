package com.halftonestudio;

import java.util.Locale;

/**
 * ADR-0001: the single physical coordinate frame shared by every cut sheet of one export.
 *
 * A frame is the artwork size, the registration margin and the four mark centers (artwork
 * coordinates). It is resolved in exactly one way for every exporter and for the editor
 * preview: {@link RegistrationMarks#artworkLayout}, which honors custom marks, circle-crop
 * corner marks and circle-crop margin compaction. Exporters that used plain
 * {@code RegistrationMarks.layout} ignored circle crop, so their marks differed from the
 * preview and from the Snip base sheet (defects D3/D4 in the 2026-11-21 assessment).
 *
 * The SVG convention is the one the standard, stencil, cell and tessellation writers already
 * share: {@code viewBox="0 0 boardW boardH"} with artwork content placed at +margin. Writers
 * that emit artwork-space geometry use {@link #open}/{@link #CLOSE}, which wrap it in a
 * {@code translate(margin margin)} group, so CutSheetZipWriter sees one identical root frame.
 */
final class SheetFrame {
    /** Registration margin of suite-style exports and their Snip base sheet. */
    static final float SPECIALTY_PADDING_MM=8f;
    static final String CLOSE="</g></svg>\n";

    final float artworkWidthMm,artworkHeightMm,paddingMm;
    private final float[][] marks;

    private SheetFrame(float w,float h,float pad,float[][] marks){
        artworkWidthMm=w;artworkHeightMm=h;paddingMm=pad;this.marks=marks;
    }

    /**
     * @param customMarks user-placed mark centers, or null for automatic marks
     * @param requestedPaddingMm margin when marks need no more (exporter's outer spacing)
     * @param circleCrop automatic marks go into the unused corners of a circular crop,
     *                   exactly where the editor preview draws them
     */
    static SheetFrame of(float widthMm,float heightMm,boolean registration,float markMm,float clearanceMm,
                         float[][] customMarks,float requestedPaddingMm,boolean circleCrop){
        if(!Float.isFinite(widthMm)||!Float.isFinite(heightMm)||widthMm<=0||heightMm<=0)
            throw new IllegalArgumentException("Invalid artwork dimensions");
        if(!registration)return new SheetFrame(widthMm,heightMm,Math.max(requestedPaddingMm,0f),null);
        RegistrationMarks.Layout layout=RegistrationMarks.artworkLayout(widthMm,heightMm,markMm,clearanceMm,
            customMarks,requestedPaddingMm,circleCrop);
        return new SheetFrame(widthMm,heightMm,layout.paddingMm,layout.positions);
    }

    boolean registration(){return marks!=null;}
    /** Mark centers in artwork coordinates (a copy), or null without registration. */
    float[][] marks(){return marks==null?null:RegistrationMarks.copyPositions(marks);}
    float boardWidthMm(){return artworkWidthMm+2*paddingMm;}
    float boardHeightMm(){return artworkHeightMm+2*paddingMm;}

    static String mm(float v){return String.format(Locale.US,"%.3f",v);}

    /** Root element plus the artwork-space group carrying this sheet's vinyl color. */
    static String open(float boardW,float boardH,float pad,int fillArgb){
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\""+mm(boardW)+"mm\" height=\""+mm(boardH)+
            "mm\" viewBox=\"0 0 "+mm(boardW)+' '+mm(boardH)+"\">\n<g transform=\"translate("+mm(pad)+' '+mm(pad)+
            ")\" fill=\""+hex(fillArgb)+"\">\n";
    }
    String open(int fillArgb){return open(boardWidthMm(),boardHeightMm(),paddingMm,fillArgb);}

    /** Appends the four marks in artwork coordinates (inside the translated group). */
    void appendMarks(StringBuilder out,int markStyle,float markMm,float minimumCutMm){
        if(marks==null)return;
        for(float[] m:marks)out.append(RegistrationMarks.svgMark(markStyle,m[0],m[1],markMm,minimumCutMm));
    }

    static String hex(int argb){return String.format(Locale.US,"#%06X",argb&0xffffff);}
}

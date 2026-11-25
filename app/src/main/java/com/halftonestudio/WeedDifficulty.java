package com.halftonestudio;

import java.util.List;
import java.util.Locale;

/**
 * Build 554 per-sheet weeding difficulty estimate (0..100) from final cut geometry.
 *
 *  - density:   cut elements per 1000 mm² of artboard (many cuts = slow weeding), up to 40 points
 *  - tiny:      share of pieces/holes smaller than a 3 mm square or unable to hold a disk of the
 *               minimum cut width (these lift with the waste or stay stuck), up to 45 points
 *  - narrow:    width/contact findings from the final sheet inspection, up to 15 points
 *
 * It is an advisory planning aid, recorded in cut-complexity.csv and shown after export;
 * it never blocks an export and is not a substitute for a scrap cut.
 */
final class WeedDifficulty {
    static final double TINY_AREA_MM2=9.0;
    private WeedDifficulty() { }

    static final class Score {
        final int value;final String rating;final int tiny,elements;
        Score(int value,int tiny,int elements){
            this.value=value;this.tiny=tiny;this.elements=elements;
            rating=value<25?"EASY":value<50?"MODERATE":value<75?"HARD":"EXPERT";
        }
    }

    static double ringArea(float[] xy){
        double a=0;int n=xy.length/2;
        for(int i=0;i<n;i++){int j=(i+1)%n;a+=xy[2*i]*(double)xy[2*j+1]-xy[2*j]*(double)xy[2*i+1];}
        return Math.abs(a)/2;
    }

    static Score score(List<float[]> loops,List<double[]> circles,int openPaths,double widthMm,double heightMm,
                       double minimumMm,int narrowFindings){
        int elements=loops.size()+circles.size();
        int tiny=0;
        for(float[] loop:loops){double a=ringArea(loop);if(a<TINY_AREA_MM2||CutIntelligence.cannotContainMinimumDisk(a,minimumMm))tiny++;}
        for(double[] c:circles){double a=Math.PI*c[2]*c[2];if(a<TINY_AREA_MM2||CutIntelligence.cannotContainMinimumDisk(a,minimumMm))tiny++;}
        double area=Math.max(1,widthMm*heightMm);
        double density=elements/(area/1000.0);
        double value=Math.min(40,density*4)+(elements==0?0:45.0*tiny/elements)+Math.min(15,narrowFindings*3);
        if(elements==0&&openPaths>0)value=0; // draw-only sheet: nothing to weed
        return new Score((int)Math.round(Math.max(0,Math.min(100,value))),tiny,elements);
    }

    /** Hardest sheet from a cut-complexity.csv that carries weed columns, or null if absent. */
    static String hardest(String complexityCsv){
        if(complexityCsv==null)return null;
        String[] lines=complexityCsv.split("\n");
        if(lines.length<2)return null;
        String[] head=lines[0].split(",");
        int sheetCol=-1,scoreCol=-1,ratingCol=-1;
        for(int i=0;i<head.length;i++){
            if(head[i].equals("sheet"))sheetCol=i;
            if(head[i].equals("weed_score"))scoreCol=i;
            if(head[i].equals("weed_rating"))ratingCol=i;
        }
        if(sheetCol<0||scoreCol<0||ratingCol<0)return null;
        int best=-1;String result=null;
        for(int r=1;r<lines.length;r++){
            String[] f=lines[r].split(",");
            if(f.length<=Math.max(scoreCol,ratingCol))continue;
            try{
                int v=Integer.parseInt(f[scoreCol].trim());
                if(v>best){best=v;result=String.format(Locale.US,"%s (%d/100) on %s",f[ratingCol],v,f[sheetCol]);}
            }catch(NumberFormatException ignored){}
        }
        return result;
    }
}

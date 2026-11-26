package com.halftonestudio;

import java.util.List;
import java.util.Locale;

/**
 * M4: what the cut-ready stage removed, so repaired geometry is never concealed.
 *
 * {@link VinylStyleSuite#prepareForCutting} may delete pieces narrower than the cut minimum
 * and fill holes narrower than the weed gap (CutGeometryConditioner). This compares each
 * sheet's generated and cut-ready topology (even-odd nesting: outer pieces vs holes) and
 * reports the NET reduction per sheet. Net, because repair can also split a pinched piece
 * in two; the counts are therefore a lower bound on what was removed, never an overstatement.
 * Android-independent.
 */
final class CutRepairSummary {
    /** Loops per sheet above which topology is not counted (bounded O(n^2) with bbox culling). */
    static final int MAX_LOOPS=8000;
    final int[] piecesRemoved,holesFilled;
    final boolean[] counted;
    final int totalPieces,totalHoles;

    private CutRepairSummary(int[] pieces,int[] holes,boolean[] counted){
        piecesRemoved=pieces;holesFilled=holes;this.counted=counted;
        int p=0,h=0;for(int i=0;i<pieces.length;i++){p+=pieces[i];h+=holes[i];}
        totalPieces=p;totalHoles=h;
    }
    static final CutRepairSummary NONE=new CutRepairSummary(new int[0],new int[0],new boolean[0]);

    static CutRepairSummary of(StyleKit.Result generated,StyleKit.Result cut){
        if(generated==null||cut==null||generated.layers.size()!=cut.layers.size())return NONE;
        int n=cut.layers.size();int[] pieces=new int[n],holes=new int[n];boolean[] counted=new boolean[n];
        for(int i=0;i<n;i++){
            List<float[]> a=generated.layers.get(i).loops,b=cut.layers.get(i).loops;
            if(a==b||a.equals(b)){counted[i]=true;continue;} // sheet untouched by repair
            if(a.size()>MAX_LOOPS||b.size()>MAX_LOOPS)continue;
            int[] before=topology(a),after=topology(b);
            pieces[i]=Math.max(0,before[0]-after[0]);holes[i]=Math.max(0,before[1]-after[1]);counted[i]=true;
        }
        return new CutRepairSummary(pieces,holes,counted);
    }
    boolean any(){return totalPieces>0||totalHoles>0;}
    boolean complete(){for(boolean c:counted)if(!c)return false;return true;}

    /** One sentence for the status line; empty when nothing was removed. */
    String sentence(float minimumMm,float gapMm){
        if(!any())return complete()?"":"Some dense sheets were not counted for repairs.";
        StringBuilder s=new StringBuilder("To meet the ");
        s.append(String.format(Locale.US,"%.2f mm minimum and %.2f mm weed gap, cut prep ",minimumMm,gapMm));
        if(totalHoles>0)s.append("filled ").append(totalHoles).append(totalHoles==1?" hole":" holes");
        if(totalHoles>0&&totalPieces>0)s.append(" and ");
        if(totalPieces>0)s.append("removed ").append(totalPieces).append(totalPieces==1?" piece":" pieces");
        s.append(". The preview shows the result.");
        return s.toString();
    }
    /** Status-line form: short enough for a phone; the full sentence goes into the export README. */
    String shortSentence(float minimumMm,float gapMm){
        if(!any())return complete()?"":"Cut-ready · some dense sheets were not counted for repairs. Tap for Cut Check.";
        StringBuilder s=new StringBuilder("Cut-ready · cut prep ");
        // When both counts are present, keep both in the compact status. The full
        // physical limits are already in sentence() and the detailed Cut Check.
        if(totalHoles>0&&totalPieces>0){
            s.append("filled ").append(totalHoles).append(totalHoles==1?" hole":" holes")
                .append(", removed ").append(totalPieces).append(totalPieces==1?" piece":" pieces")
                .append(". Cut Check; test-cut first.");
            return s.toString();
        }
        // Holes are filled because of the weed gap, pieces removed because of the minimum width.
        if(totalHoles>0)s.append("filled ").append(totalHoles).append(totalHoles==1?" hole":" holes")
            .append(String.format(Locale.US," (%.2f mm gap)",gapMm));
        if(totalHoles>0&&totalPieces>0)s.append(", ");
        if(totalPieces>0)s.append("removed ").append(totalPieces).append(totalPieces==1?" piece":" pieces")
            .append(String.format(Locale.US," (%.2f mm min)",minimumMm));
        s.append(". Tap for Cut Check; test-cut first.");
        return s.toString();
    }
    /** Per-sheet CSV for the export ZIP. */
    String csv(StyleKit.Result cut){
        StringBuilder s=new StringBuilder("sheet,name,pieces_removed_net,holes_filled_net,counted\n");
        for(int i=0;i<piecesRemoved.length;i++)
            s.append(i+1).append(',').append(cut.layers.get(i).name.replace(',','_')).append(',')
             .append(piecesRemoved[i]).append(',').append(holesFilled[i]).append(',').append(counted[i]).append('\n');
        return s.toString();
    }

    /** [outer pieces, holes] by even-odd nesting depth of each loop's first vertex. */
    static int[] topology(List<float[]> loops){
        int n=loops.size();float[][] box=new float[n][];
        for(int i=0;i<n;i++){
            float[] p=loops.get(i);float a=Float.POSITIVE_INFINITY,c=a,b=Float.NEGATIVE_INFINITY,d=b;
            for(int k=0;k+1<p.length;k+=2){a=Math.min(a,p[k]);b=Math.max(b,p[k]);c=Math.min(c,p[k+1]);d=Math.max(d,p[k+1]);}
            box[i]=new float[]{a,c,b,d};
        }
        int outer=0,holes=0;
        for(int i=0;i<n;i++){
            if((i&63)==0)StyleKit.checkCancelled();
            float[] p=loops.get(i);if(p.length<6)continue;
            float x=p[0],y=p[1];int depth=0;
            for(int j=0;j<n;j++){
                if(j==i||x<box[j][0]||x>box[j][2]||y<box[j][1]||y>box[j][3])continue;
                if(box[j][2]-box[j][0]<box[i][2]-box[i][0]&&box[j][3]-box[j][1]<box[i][3]-box[i][1])continue; // smaller cannot contain
                if(MaskedFacetClipper.inside(loops.get(j),x,y))depth++;
            }
            if((depth&1)==0)outer++;else holes++;
        }
        return new int[]{outer,holes};
    }
}

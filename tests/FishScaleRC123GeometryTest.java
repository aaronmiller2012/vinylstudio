package com.halftonestudio;

import java.util.HashMap;
import java.util.Map;

/** Host regression for true exposed shield-shaped fish scales, without Android. */
public final class FishScaleRC123GeometryTest {
    private static void check(boolean b,String note){if(!b)throw new AssertionError(note);}
    private static final int W=160,H=184;
    private static int[] pixels(boolean dark){
        int[] a=new int[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            int v=dark?22:65+(x*100/W)+(y*55/H);
            a[y*W+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        return a;
    }
    private static LayeredCellPortraitEngine.Options options(){
        LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
        o.widthMm=279.4f;o.columns=30;o.layerCount=8;
        o.gapMm=.24f;o.minFeatureMm=.254f;o.photoDetail=1f;
        o.detailMotifOverlay=true;o.registration=false;
        return o;
    }
    private static float min(float[] a,int offset){float v=Float.POSITIVE_INFINITY;for(int j=offset;j<a.length;j+=2)v=Math.min(v,a[j]);return v;}
    private static float max(float[] a,int offset){float v=Float.NEGATIVE_INFINITY;for(int j=offset;j<a.length;j+=2)v=Math.max(v,a[j]);return v;}
    public static void main(String[] args){
        LayeredCellPortraitEngine.Options o=options();
        LayeredCellPortraitEngine.Result result=FishScaleMosaicEngine.generate(pixels(false),W,H,o);
        Map<Integer,LayeredCellPortraitEngine.Piece> body=new HashMap<>();
        int bodyCount=0,highlightCount=0;
        float pitch=W/(float)result.columns,rowPitch=pitch*.69f;
        for(LayeredCellPortraitEngine.Piece piece:result.pieces){
            check(piece.outer.length>=6&&piece.outer.length%2==0,"Invalid polygon");
            if(piece.cell<0)continue;
            float minX=min(piece.outer,0),maxX=max(piece.outer,0);
            float minY=min(piece.outer,1),maxY=max(piece.outer,1);
            int row=piece.cell/(result.columns+2)-1;
            // The new shared-edge lattice intentionally crosses nominal row bands;
            // adjacent rows meet along the same scalloped boundary instead of
            // producing isolated oval pieces.
            check(minX>=-1e-3&&maxX<=W+1e-3&&minY>=-1e-3&&maxY<=H+1e-3,"Scale out of canvas");
            if(piece.layer==7){highlightCount++;continue;}
            check(!body.containsKey(piece.cell),"Multiple full scales in one tile");
            body.put(piece.cell,piece);bodyCount++;
            check(StyleKit.selfIntersections(piece.outer,1)==0,"Self-intersecting scale");
            // The reference shape has a near-pointed attachment, curved shoulders
            // and a rounded central free edge, not the old broad shield top.
            if(minX>pitch&&maxX<W-pitch && minY>rowPitch&&maxY<H-rowPitch){
                float width=maxX-minX,height=maxY-minY;
                check(height>rowPitch*.7f,"Scale too shallow");
                float topWidth=0f,topMin=Float.POSITIVE_INFINITY,topMax=Float.NEGATIVE_INFINITY;
                for(int i=0;i<piece.outer.length;i+=2){
                    if(piece.outer[i+1]<=minY+.03f*height){
                        topMin=Math.min(topMin,piece.outer[i]);topMax=Math.max(topMax,piece.outer[i]);
                    }
                }
                topWidth=topMax-topMin;
                check(topWidth<width*.34f && topWidth>=0,
                    "Upper scale edge is not a narrow cusp: "+topWidth+" vs "+width);
                boolean roundedTip=false;
                for(int i=0;i<piece.outer.length;i+=2){
                    float px=piece.outer[i],py=piece.outer[i+1];
                    if(Math.abs(px-(minX+maxX)*.5f)<width*.06f &&
                       py>=maxY-height*.005f)roundedTip=true;
                }
                check(roundedTip,"Free margin lacks a rounded central tip");
            }
        }
        check(bodyCount>300,"Too few photographed scales");
        check(highlightCount>20,"Highlight treatment vanished from bright parts");
        for(LayeredCellPortraitEngine.Piece accent:result.pieces){
            if(accent.layer!=7||accent.cell<0)continue;
            LayeredCellPortraitEngine.Piece scale=body.get(accent.cell);
            check(scale!=null,"Highlight without supporting scale");
            if(min(scale.outer,0)>1 && max(scale.outer,0)<W-1 &&
               min(scale.outer,1)>1 && max(scale.outer,1)<H-1)
            for(int i=0;i<accent.outer.length;i+=2){
                check(scale.contains(accent.outer[i],accent.outer[i+1]),
                    "Highlight left parent scale cell="+accent.cell+" at "+accent.outer[i]+","+accent.outer[i+1]+" (index "+i+")");
            }
            check(StyleKit.selfIntersections(accent.outer,1)==0,"Highlight self-intersection");
        }
        for(int i=0;i<result.contourCounts.length;i++)
            check(result.contourCounts[i]<4800,"Path budget exceeded");
        String svg=result.svg(0)+result.svg(7);
        check(svg.equals(FishScaleMosaicEngine.generate(pixels(false),W,H,o).svg(0)+
            FishScaleMosaicEngine.generate(pixels(false),W,H,o).svg(7)),"Nondeterministic SVG");
        LayeredCellPortraitEngine.Result shadows=FishScaleMosaicEngine.generate(pixels(true),W,H,o);
        check(shadows.pieceCounts[7]==0,"Repetitive bright highlight cuts appear in deep shadows");
        LayeredCellPortraitEngine.Options high=o.copy();high.columns=40;
        check(FishScaleMosaicEngine.generate(pixels(false),W,H,high).pieces.size()>200,
            "High-density rendering failed");
        LayeredCellPortraitEngine.Options cropped=o.copy();
        cropped.allowedPixels=new boolean[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)
            cropped.allowedPixels[y*W+x]=Math.pow((x-80)/72.0,2)+Math.pow((y-95)/86.0,2)<1;
        LayeredCellPortraitEngine.Result mask=FishScaleMosaicEngine.generate(pixels(false),W,H,cropped);
        check(mask.pieces.size()>100,"Snip clipping destroyed fish scales");
        System.out.println("PASS RC130 scallop scales, near-pointed rounded apex, free tips, contained selective glints, clipped masks, high density and deterministic SVG: "+bodyCount+" scales, "+highlightCount+" accents");
    }
}

package com.halftonestudio;

import java.lang.reflect.Method;

/** Fish-scale reference regression: shared-edge scallops, not isolated ovals. */
public final class FishScaleRC131LatticeTest {
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private static int[] photo(int w,int h){
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=35+((x*179/w+y*99/h)%215);
            pixels[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
        }
        return pixels;
    }
    private static float minY(float[] polygon){float m=Float.POSITIVE_INFINITY;for(int i=1;i<polygon.length;i+=2)m=Math.min(m,polygon[i]);return m;}
    private static float maxY(float[] polygon){float m=Float.NEGATIVE_INFINITY;for(int i=1;i<polygon.length;i+=2)m=Math.max(m,polygon[i]);return m;}
    public static void main(String[] args) throws Exception {
        int w=140,h=160;
        LayeredCellPortraitEngine.Options o=new LayeredCellPortraitEngine.Options();
        o.widthMm=200f;o.columns=18;o.layerCount=8;o.photoDetail=1f;
        o.gapMm=.20f;o.minFeatureMm=.254f;o.registration=false;
        o.detailMotifOverlay=false;
        LayeredCellPortraitEngine.Result result=FishScaleMosaicEngine.generate(photo(w,h),w,h,o);
        require(result.pieces.size()>200,"Too few scales");
        // Raster sample each body polygon, across ALL color layers. Full
        // tessellation without same-color or cross-color duplicate cuts.
        int visible=0,covered=0,multiple=0;
        for(int yy=2;yy<h-2;yy++)for(int xx=2;xx<w-2;xx++){
            int hits=0;float px=xx+.5f,py=yy+.5f;
            for(LayeredCellPortraitEngine.Piece piece:result.pieces)
                if(piece.contains(px,py))hits++;
            visible++;if(hits>0)covered++;if(hits>1)multiple++;
        }
        require(multiple==0,"Scale polygons overlap in same vinyl composition: "+multiple);
        require(covered>visible*.78,"Scale lattice is too sparse (oval-like): "+covered+"/"+visible);

        // At the center of a tile the upper edge must rise into a junction,
        // whereas the lower edge bulges into one broad free scallop.
        Method shape=LayeredCellPortraitEngine.class.getDeclaredMethod("fishScaleShape",
            float.class,float.class,int.class,float.class,float.class,float.class,float.class,float.class,float.class);
        shape.setAccessible(true);
        float pitch=12f,rowPitch=pitch*.69f,amplitude=rowPitch*.76f,gap=.25f;
        float[] poly=(float[])shape.invoke(null,0f,pitch,4,rowPitch,pitch,0f,pitch*.5f,amplitude,gap);
        require(StyleKit.selfIntersections(poly,1)==0,"Lattice scale self-intersects");
        require(maxY(poly)-minY(poly)>rowPitch*1.5f,"Scale still flattened into an oval");
        float topCenter=poly[2*12+1];
        float topSide=poly[1];
        float bottomCenter=poly[(25+12)*2+1];
        float bottomSide=poly[(25)*2+1];
        require(topCenter<topSide-amplitude*.50f,"No rising upper junction");
        require(bottomCenter>bottomSide+amplitude*.50f,"No rounded lower scallop");
        // Snip-edge regression: clipped tiles must remain inside the mask,
        // and raster contour fallback must not duplicate cut interiors.
        LayeredCellPortraitEngine.Options cropped=o.copy();
        cropped.allowedPixels=new boolean[w*h];
        for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++){
            float dx=(xx-w*.5f)/(w*.44f),dy=(yy-h*.5f)/(h*.45f);
            cropped.allowedPixels[yy*w+xx]=dx*dx+dy*dy<1f;
        }
        LayeredCellPortraitEngine.Result clipped=FishScaleMosaicEngine.generate(photo(w,h),w,h,cropped);
        for(int yy=4;yy<h-4;yy+=2)for(int xx=4;xx<w-4;xx+=2){
            int hits=0;
            for(LayeredCellPortraitEngine.Piece piece:clipped.pieces){
                if(piece.contains(xx+.5f,yy+.5f))hits++;
            }
            require(hits<=1,"Snip edge has overlapping same-sheet scale contours at "+xx+","+yy);
        }
        String first=result.svg(0);
        require(first.equals(FishScaleMosaicEngine.generate(photo(w,h),w,h,o).svg(0)),"Unstable SVG");
        System.out.println("PASS RC131 scalloped shared-edge lattice, no overlap, coverage="+covered+"/"+visible+", "+result.pieces.size()+" closed pieces");
    }
}

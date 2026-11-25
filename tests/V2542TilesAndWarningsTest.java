package com.halftonestudio;

import java.util.ArrayList;
import java.util.List;

public final class V2542TilesAndWarningsTest {
    private static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        int width=1081,height=805;
        List<SourceTilePlan.Tile> tiles=SourceTilePlan.create(width,height,512,2);
        ok(tiles.size()==6,"Expected two rows x three columns");
        int[] coverage=new int[width*height];
        for(SourceTilePlan.Tile tile:tiles){
            ok(tile.localX()>=0&&tile.localY()>=0,"Invalid local/global mapping");
            ok(tile.decodeX0<=tile.x0&&tile.decodeX1>=tile.x1,"Missing horizontal halo");
            ok(tile.decodeY0<=tile.y0&&tile.decodeY1>=tile.y1,"Missing vertical halo");
            ok(tile.decodeX0>=0&&tile.decodeY0>=0&&tile.decodeX1<=width&&tile.decodeY1<=height,"Halo outside source");
            for(int y=tile.y0;y<tile.y1;y++)for(int x=tile.x0;x<tile.x1;x++)coverage[y*width+x]++;
        }
        for(int n:coverage)ok(n==1,"Lost or duplicated output pixel at tile seam");
        boolean[] mask=new boolean[64*48];
        // One large safe component and a tiny isolated cut close to a tile boundary.
        for(int y=8;y<39;y++)for(int x=7;x<50;x++)mask[y*64+x]=true;
        mask[43*64+57]=true;
        List<CutWarningLocator.Marker> warnings=new ArrayList<>();
        SpecialtyCutWarningLocator.mask(warnings,mask,64,48,2,.20f,.65f,80);
        ok(warnings.size()==1,"Expected just the tiny isolated piece, not a false positive on the large region");
        CutWarningLocator.Marker mark=warnings.get(0);
        ok(mark.layer==2&&Math.abs(mark.xMm-11.50f)<.11f&&Math.abs(mark.yMm-8.70f)<.11f,"Incorrect physical marker position");
        TessellationEngine.Options opts=new TessellationEngine.Options();opts.widthMm=80f;opts.layerCount=3;
        ArrayList<TessellationEngine.Tile> tilesOut=new ArrayList<>();
        tilesOut.add(new TessellationEngine.Tile(new float[]{3,3,4,3,4,20,3,20},1,.5f));
        tilesOut.add(new TessellationEngine.Tile(new float[]{10,10,30,10,30,30,10,30},2,.5f));
        TessellationEngine.Result result=new TessellationEngine.Result(40,40,opts,tilesOut);
        List<CutWarningLocator.Marker> located=SpecialtyCutWarningLocator.locate(result,2.5f,8);
        ok(located.size()==1&&located.get(0).layer==1,"Tessellation risk must retain correct layer ownership");
        ok(SpecialtyCutWarningLocator.locate(result,2.5f,0).isEmpty(),"Zero marker budget not honored");
        System.out.println("PASS v2.54.2 source tile global coverage, halo joins, isolated masks, specialty layers");
    }
}

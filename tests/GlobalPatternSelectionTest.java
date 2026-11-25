package com.halftonestudio;
import java.util.*;

/** Reproduces the real Shapes->Layers override bug and checks generated SVG geometry. */
public final class GlobalPatternSelectionTest {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static int[] photo(int w,int h){int[] rgb=new int[w*h];Arrays.fill(rgb,0xff484848);return rgb;}
    private static VinylEngine.Settings fixture(){
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        s.widthMm=100;s.marginMm=3;s.minimumMm=.3f;s.masterSpacingMm=4f;s.registration=false;
        return s;
    }
    private static void selectedStyleOverridesSavedEllipses() {
        VinylEngine.Settings s=fixture();
        for(VinylEngine.Layer l:s.layers)l.lineStyleOverride=VinylEngine.ELLIPSE;
        SmartHalftonePresets.apply(s,VinylEngine.LINE,1,100/25.4f,110/25.4f);
        require(s.layers.get(0).lineStyleOverride==VinylEngine.ELLIPSE,
                "Test fixture must reproduce original stale override");
        VinylEngine.Result bad=VinylEngine.generate(photo(60,66),60,66,s);
        require(!bad.shapes.get(0).isEmpty(),"Fixture should generate shapes");
        for(VinylEngine.Shape sh:bad.shapes.get(0))
            require(sh.xy!=null&&sh.vertexCount()==14,"Baseline must be ellipses");
        GlobalPatternSelection.apply(s,VinylEngine.LINE,1,100/25.4f,110/25.4f);
        for(VinylEngine.Layer l:s.layers)require(l.lineStyleOverride==-1,"Selecting global line style must clear a layer override");
        VinylEngine.Result good=VinylEngine.generate(photo(60,66),60,66,s);
        int maxVertices=0;
        for(int li=0;li<good.shapes.size();li++)for(VinylEngine.Shape sh:good.shapes.get(li)){
            require(!sh.circle,"Line screen must generate cuttable ribbon polygons, not circular dots");
            maxVertices=Math.max(maxVertices,sh.vertexCount());
        }
        require(maxVertices>20,"Expected continuous ribbon polygons, not ellipse motifs");
        require(!VinylEngine.svg(good,0).equals(VinylEngine.svg(bad,0)),"SVG must reflect selected global style");
        // A deliberate later per-layer selection is still permitted.
        s.layers.get(1).lineStyleOverride=VinylEngine.STAR;
        require(s.layers.get(1).lineStyleOverride==VinylEngine.STAR,"Layers override must remain user-editable");
        // Changing profile is not a style selection and must not erase it.
        SmartHalftonePresets.apply(s,s.pattern,2,100/25.4f,110/25.4f);
        require(s.layers.get(1).lineStyleOverride==VinylEngine.STAR,"Profile changes must preserve individual layer styles");
    }
    private static void allShapesRenderTheirOwnGeometry() {
        int[] pixels=photo(60,66);
        int[][] styles={{VinylEngine.ROUND,12},{VinylEngine.SQUARE,4},{VinylEngine.ELLIPSE,14},
            {VinylEngine.DIAMOND,4},{VinylEngine.HEX,6},{VinylEngine.STAR,10},
            {VinylEngine.CROSS,12},{VinylEngine.TRIANGLE,3},{VinylEngine.OCTAGON,8},
            {VinylEngine.ROUNDED_SQUARE,16},{VinylEngine.TEARDROP,12},
            {VinylEngine.HEART,20},{VinylEngine.CAPSULE,12},{VinylEngine.DASH,4}};
        for(int[] entry:styles){
            VinylEngine.Settings s=fixture();
            for(VinylEngine.Layer l:s.layers)l.lineStyleOverride=VinylEngine.ELLIPSE;
            GlobalPatternSelection.apply(s,entry[0],1,100/25.4f,110/25.4f);
            VinylEngine.Result r=VinylEngine.generate(pixels,60,66,s);
            require(!r.shapes.get(0).isEmpty(),"Empty result for "+VinylEngine.PATTERNS[entry[0]]);
            VinylEngine.Shape first=r.shapes.get(0).get(0);
            require(first.vertexCount()==entry[1],"Wrong shape family for "+VinylEngine.PATTERNS[entry[0]]+": "+first.vertexCount());
            require(first.circle==(entry[0]==VinylEngine.ROUND),"Circle-only rendering unexpected for "+VinylEngine.PATTERNS[entry[0]]);
        }
    }
    public static void main(String[] args){selectedStyleOverridesSavedEllipses();allShapesRenderTheirOwnGeometry();
        System.out.println("PASS: global Shapes selection defeats stale ellipse overrides; lines and 14 shape families emit expected SVG geometry; Layers overrides and profile persistence retained");}
}

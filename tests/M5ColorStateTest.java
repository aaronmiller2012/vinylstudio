package com.halftonestudio;

import java.util.Arrays;

/** M5 colour-state invariants on the real palette functions (no source-text checks). */
public final class M5ColorStateTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static final int[] SURFACES={0xffffffff,0xff000000,0xff1e3a8a,0xffe8d8b0,0xff808080,0xffc03020};

    public static void main(String[] a){
        int[] styles={VinylStyleSuite.CONFETTI,VinylStyleSuite.CONFETTI_FALLEN,VinylStyleSuite.CHAINMAIL,
            VinylStyleSuite.GLASS,VinylStyleSuite.TOPOGRAPHIC,VinylStyleSuite.REDUCTION_WOODCUT,
            VinylStyleSuite.POSTER_STENCIL,VinylStyleSuite.CAMOUFLAGE,VinylStyleSuite.QUILLED_MARBLING,
            VinylStyleSuite.LOW_POLY};
        // 1. Mounting surface never reorders / recolours the palette of surface-independent families,
        //    and never touches a photo-derived palette for ANY style.
        for(int pid=0;pid<VinylPalettes.NAMES.length;pid++){
            int[] eight=VinylPalettes.get(pid,8);int[] keep=eight.clone();
            for(int style:styles)for(int n=Math.max(3,2);n<=8;n++)for(boolean rev:new boolean[]{false,true}){
                int[] photoBase=VinylPaletteMapping.forStyle(style,n,SURFACES[0],eight,rev,true);
                for(int s:SURFACES)check(Arrays.equals(photoBase,VinylPaletteMapping.forStyle(style,n,s,eight,rev,true)),
                    "photo palette changed with surface style "+style);
                if(PaletteStylePolicy.familyForNewStyle(style)==PaletteStylePolicy.PHOTO_CELLS)continue; // surface-aware at explicit apply only
                int[] ref=VinylPaletteMapping.forStyle(style,n,SURFACES[0],eight,rev,false);
                for(int s:SURFACES)check(Arrays.equals(ref,VinylPaletteMapping.forStyle(style,n,s,eight,rev,false)),
                    "named palette "+pid+" changed with surface for style "+style);
                // every input swatch survives exactly once (no recolour, nothing lost)
                int[] x=ref.clone(),y=eight.clone();Arrays.sort(x);Arrays.sort(y);
                check(Arrays.equals(x,y)||style==VinylStyleSuite.GLASS,"swatch multiset changed "+style);
            }
            check(Arrays.equals(keep,eight),"input mutated");
        }
        // 2. Global Reverse is an involution for every orientation helper and every count.
        int[] eight=VinylPalettes.get(0,8);
        for(int n=2;n<=8;n++)for(boolean lead:new boolean[]{false,true}){
            int[] r=PaletteOrientation.reverseActive(eight,n,true,lead);
            check(Arrays.equals(PaletteOrientation.reverseActive(r,n,true,lead),eight),"reverseActive involution "+n);
            check(Arrays.equals(PaletteOrientation.reverseActive(eight,n,false,lead),eight),"reverse off is identity");
            if(lead)check(r[0]==eight[0],"lead moved");
            for(int i=n;i<8;i++)check(r[i]==eight[i],"parked swatch moved");
            int[] cm=PaletteOrientation.orientCmykActive(eight,n,true);
            check(Arrays.equals(PaletteOrientation.orientCmykActive(cm,n,true),eight),"cmyk involution "+n);
            check(cm[0]==eight[0]&&cm[n-1]==eight[n-1],"cmyk anchors");
        }
        // 3. Physical reverse maps back onto stable source slots (involution through two applications).
        int[] over={1,2,3,4,5,6,7,8},order={0,1,2,3,4,5,6,7};
        int[] disp={0xff010101,0xff020202,0xff030303,0xff040404,0xff050505,0xff060606,0xff070707,0xff080808};
        int[] once=PhysicalLayerColorReverse.reverse(new int[8],disp,order,0);
        int[] shown=once; // overrides now hold the reversed stack at their source slots
        int[] twice=PhysicalLayerColorReverse.reverse(new int[8],shown,order,0);
        check(Arrays.equals(twice,disp),"PhysicalLayerColorReverse involution");
        // 4. Default classic CMYK physical stack: yellow bottom, black top, and Reverse keeps both.
        VinylEngine.Settings s=VinylEngine.Settings.defaults();
        check(s.layers.get(0).cmykChannel==2&&s.layers.get(0).color==0xffffdd00,"yellow must be L1");
        check(s.layers.get(3).cmykChannel==3&&s.layers.get(3).color==0xff131313,"black must be uppermost");
        int[] stack=new int[4];for(int i=0;i<4;i++)stack[i]=s.layers.get(i).color;
        int[] rv=PaletteOrientation.orientCmyk(stack,true);
        check(rv[0]==stack[0]&&rv[3]==stack[3],"Reverse keeps Y bottom / K top");
        // 5. Export names carry exactly the edited colour and the stack index.
        for(int i=0;i<8;i++){
            String f=ExportColorLabels.fileName(i,disp[i]);
            check(f.startsWith(String.format("%02d-apply-L%d-",i+1,i+1))&&f.contains(String.format("%06x",disp[i]&0xffffff)),f);
            check(ExportColorLabels.physicalLabel(i,disp[i],-1).contains(ExportColorLabels.hex(disp[i])),"label hex");
        }
        System.out.println("PASS M5 colour-state invariants (surface, reverse, CMYK order, export names)");
    }
}

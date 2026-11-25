package com.halftonestudio;
import java.util.Arrays;
import java.util.HashSet;

public final class PaletteHarmonyRC81Test {
    static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    public static void main(String[] args){
        // 19 established standard IDs + 7 creative IDs. None were removed; M4 appends only
        // (the exact legacy names/colours are pinned by M4ColorSchemesTest).
        ok(VinylPalettes.NAMES.length>=19,"Stable catalog IDs");
        ok(ColorWorkspacePalette.TESS_NAMES.length==7,"Creative IDs");
        int tested=0;
        for(int paper:new int[]{0xfffaf9f6,0xfff4e7ce,0xff18191e}){
            for(int which=0;which<PaletteRecommendations.CATALOG_SIZE;which++){
                int[] colors=PaletteRecommendations.colors(which);
                ok(colors.length==8,"Eight slots");
                ok(new HashSet<Integer>(){{for(int c:colors)add(c);}}.size()==8,"Distinct slots for preset "+which);
                double minL=Double.POSITIVE_INFINITY,maxL=Double.NEGATIVE_INFINITY;
                for(int c:colors){minL=Math.min(minL,ColorWorkspacePalette.lightness(c));maxL=Math.max(maxL,ColorWorkspacePalette.lightness(c));}
                ok(maxL-minL>=60,"Portrait tone span of at least 60 L* for palette "+which+" was "+(maxL-minL));
                ok(minL<50&&maxL>75,"Shadows and highlights in palette "+which);
                int[] source=colors.clone();
                for(int count=2;count<=8;count++){
                    if(count>=3){
                    int[] glass=VinylPaletteMapping.forStyle(VinylStyleSuite.GLASS,count,paper,colors,true,false);
                    ok(glass.length==8&&glass[0]==source[0],"Glass lead preserved "+which+"/"+count);
                    // RC69+ reverses only active panes. Parked colors stay in place.
                    for(int k=1;k<count;k++)ok(glass[k]==source[count-k],"Active pane reverse "+k);
                    for(int k=count;k<8;k++)ok(glass[k]==source[k],"Parked pane unchanged "+k);
                    int[] normal=VinylPaletteMapping.forStyle(VinylStyleSuite.GLASS,count,paper,colors,false,false);
                    ok(Arrays.equals(normal,source),"Glass base intact");
                    }
                    for(int style:new int[]{VinylStyleSuite.LOW_POLY,VinylStyleSuite.TEXT,VinylStyleSuite.TOPOGRAPHIC}){
                        int[] a=VinylPaletteMapping.forStyle(style,count,paper,colors,false,false);
                        int[] b=VinylPaletteMapping.forStyle(style,count,paper,colors,false,false);
                        ok(Arrays.equals(a,b),"Deterministic palette order");
                        ok(new HashSet<Integer>(){{for(int c:a)add(c);}}.size()==8,"Eight available colors");
                        for(int k=0;k<count;k++){
                            int slot=a[k];boolean exists=false;for(int c:source)if(c==slot)exists=true;
                            ok(exists,"No invented manufacturer colors");
                        }
                    }
                    tested++;
                }
                ok(Arrays.equals(colors,source),"Never mutate named preset");
            }
        }
        int[] gray={0xff777777,0xff777777,0xff777777,0xff777777,0xff777777,0xff777777,0xff777777,0xff777777};
        ok(Arrays.equals(gray,VinylPaletteMapping.forStyle(VinylStyleSuite.TOPOGRAPHIC,4,0xfffaf9f6,gray,true,true)),"Photo duplicates allowed");
        boolean rejected=false;
        try{VinylPaletteMapping.forStyle(VinylStyleSuite.GLASS,2,0xfffaf9f6,source(),false,false);}
        catch(IllegalArgumentException valid){rejected=true;}
        ok(rejected,"Glass requires lead + at least two pane colors");
        int[] process=VinylPaletteMapping.processEight(VinylStyleSuite.GLASS,8,0xfffaf9f6,true);
        ok(process[0]==0xff131313,"Black L1 process lead");
        ok(new HashSet<Integer>(){{for(int c:process)add(c);}}.size()==8,"Process 8 unique vinyl sheets");
        int[] three=VinylPaletteMapping.forStyle(VinylStyleSuite.GLASS,3,0xfffaf9f6,source(),true,false);
        int[] eight=VinylPaletteMapping.forStyle(VinylStyleSuite.GLASS,8,0xff18191e,source(),true,false);
        ok(three[0]==eight[0],"Glass lead cannot move");
        ok(three[1]==source()[2] && three[2]==source()[1],"Only active panes reverse");
        ok(eight[1]==source()[7] && eight[7]==source()[1],"Eight active panes reverse");
        System.out.println("PASS RC81: "+tested+" preset/surface/layer configurations, 8 distinct vinyl colors, lead stable, tone span, photo repeats");
    }
    private static int[] source(){return VinylPalettes.get(0,8);}
}

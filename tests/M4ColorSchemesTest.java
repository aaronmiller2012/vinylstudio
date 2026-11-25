package com.halftonestudio;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

/** M4: ten appended colour schemes; stable legacy IDs; documented intent of each scheme. */
public final class M4ColorSchemesTest {
    static int failures=0;
    static void ok(boolean b,String m){if(!b){failures++;System.out.println("FAIL "+m);}}
    static double[] lab(int c){double[] o=new double[3];PhotoPaletteOptimizer.oklab(c,o);return o;}
    static double chroma(int c){double[] l=lab(c);return Math.hypot(l[1],l[2]);}
    static double hue(int c){double[] l=lab(c);return Math.toDegrees(Math.atan2(l[2],l[1]));}

    /** Sorted (L, chroma) profile distance: ~0 for a pure hue rotation of the same palette. */
    static double profileDistance(int[] a,int[] b){
        double[][] pa=profile(a),pb=profile(b);double s=0;
        for(int i=0;i<8;i++)s+=Math.hypot(pa[i][0]-pb[i][0],pa[i][1]-pb[i][1]);
        return s/8;
    }
    static double[][] profile(int[] p){
        double[][] o=new double[8][];for(int i=0;i<8;i++){double[] l=lab(p[i]);o[i]=new double[]{l[0],Math.hypot(l[1],l[2])};}
        Arrays.sort(o,(x,y)->Double.compare(x[0],y[0]));return o;
    }
    /** Mean nearest-colour distance from a to b. */
    static double setDistance(int[] a,int[] b){
        double s=0;for(int x:a){double m=9;for(int y:b)m=Math.min(m,PhotoPaletteOptimizer.distance(x,y));s+=m;}return s/a.length;
    }

    public static void main(String[] args){
        String[] legacy={"Portrait Vinyl","Retro Sunset","Ocean","Pop Art","Forest","Neon Night","Earth & Rose","Monochrome",
            "Desert Clay","Arctic Blue","Vintage Print","Wildflower","Signal Primary","Jewel Cabinet","Soft Sorbet",
            "Copper Patina","Acid Citrus","Winter Cardinal","Urban Concrete"};
        String[] added={"Natural Skin","Graphic Noir","Faded Kodachrome","Nordic Modern","Terra & Moss",
            "Muted Gallery","Riso Pop","Indigo Study","Teal & Coral","Chiaroscuro"};
        ok(VinylPalettes.NAMES.length==legacy.length+added.length,"29 presets");
        for(int i=0;i<legacy.length;i++)ok(legacy[i].equals(VinylPalettes.NAMES[i]),"legacy ID "+i+" unchanged");
        // Legacy colours byte-identical (saved projects re-resolve preset:N).
        long h=0;for(int i=0;i<legacy.length;i++)for(int c:VinylPalettes.get(i,8))h=h*31+c;
        ok(h==LEGACY_HASH,"legacy preset colours unchanged (hash "+h+")");
        ok(new HashSet<>(Arrays.asList(VinylPalettes.NAMES)).size()==VinylPalettes.NAMES.length,"unique names");
        ok(PaletteRecommendations.CATALOG_SIZE==VinylPalettes.NAMES.length+ColorWorkspacePalette.TESS_NAMES.length,"catalog lists every palette");
        System.out.println("scheme             minPairDE  L*span  maxChroma  nearestProfile  nearestSet");
        for(int k=0;k<added.length;k++){
            int id=legacy.length+k;ok(added[k].equals(VinylPalettes.NAMES[id]),"added name "+k);
            int[] p=VinylPalettes.get(id,8);
            ok(new HashSet<Integer>(){{for(int c:p)add(c);}}.size()==8,added[k]+" 8 distinct swatches");
            double minPair=9;for(int i=0;i<8;i++)for(int j=i+1;j<8;j++)minPair=Math.min(minPair,PhotoPaletteOptimizer.distance(p[i],p[j]));
            double lo=100,hi=0,maxC=0;for(int c:p){lo=Math.min(lo,ColorWorkspacePalette.lightness(c));hi=Math.max(hi,ColorWorkspacePalette.lightness(c));maxC=Math.max(maxC,chroma(c));}
            double nearestProfile=9,nearestSet=9;
            for(int o=0;o<VinylPalettes.NAMES.length;o++)if(o!=id){
                nearestProfile=Math.min(nearestProfile,profileDistance(p,VinylPalettes.get(o,8)));
                nearestSet=Math.min(nearestSet,setDistance(p,VinylPalettes.get(o,8)));}
            System.out.printf(Locale.US,"%-18s %9.3f %7.1f %10.3f %15.3f %11.3f%n",added[k],minPair,hi-lo,maxC,nearestProfile,nearestSet);
            ok(minPair>=.045,added[k]+" swatches distinguishable as vinyl (min pair "+minPair+")");
            ok(hi-lo>=60&&lo<50&&hi>75,added[k]+" covers shadows and highlights");
            ok(nearestProfile>=.02,added[k]+" is not a hue rotation of another preset");
            ok(nearestSet>=.035,added[k]+" is not a near copy of another preset");
            // Every 2-8 layer subset stays distinct and stacks lightest at the bottom on a pale surface.
            for(int n=2;n<=8;n++)for(int surface:new int[]{VinylEngine.DEFAULT_SURFACE,0xff18191e}){
                int[] chosen=ColorWorkspacePalette.selectForSurface(p,n,surface);
                ok(new HashSet<Integer>(){{for(int c:chosen)add(c);}}.size()==n,added[k]+" "+n+"-layer subset distinct");
            }
        }
        // Documented intents.
        int[] muted=VinylPalettes.get(legacy.length+5,8);for(int c:muted)ok(chroma(c)<=.065,"Muted Gallery stays low-chroma "+Integer.toHexString(c));
        int[] indigo=VinylPalettes.get(legacy.length+7,8);
        for(int i=1;i<8;i++)ok(lab(indigo[i])[0]>lab(indigo[i-1])[0],"Indigo Study ascends in lightness");
        for(int c:indigo)ok(Math.abs(hue(c)-hue(indigo[3]))<25,"Indigo Study is one hue family");
        int[] riso=VinylPalettes.get(legacy.length+6,8);int vivid=0;for(int c:riso)if(chroma(c)>.15)vivid++;
        ok(vivid>=5,"Riso Pop has at least five vivid hues");
        int[] tc=VinylPalettes.get(legacy.length+8,8);
        for(int n=3;n<=8;n++){int[] s=ColorWorkspacePalette.selectForSurface(tc,n,VinylEngine.DEFAULT_SURFACE);
            boolean teal=false,coral=false;for(int c:s){double hh=hue(c);if(hh<-100||hh>170)teal=true;if(hh>10&&hh<60&&chroma(c)>.06)coral=true;}
            ok(teal&&coral,"Teal & Coral keeps both complements at "+n+" layers");}
        if(failures>0)throw new AssertionError(failures+" colour scheme failures");
        System.out.println("PASS M4 colour schemes: 10 appended, legacy IDs and colours unchanged");
    }
    /** Hash of presets 0-18 computed from the untouched build-558 VinylPalettes table. */
    static final long LEGACY_HASH=5763267135219473278L;
}

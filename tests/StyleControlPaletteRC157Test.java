package com.halftonestudio;

import java.util.Arrays;
import java.util.HashSet;

/** JVM checks of style-specific swatch order, structural sheets and geometry controls. */
public final class StyleControlPaletteRC157Test {
    static void ok(boolean condition,String msg){if(!condition)throw new AssertionError(msg);}
    static int[] source(){return new int[]{
        0xff101c24,0xff4f4a60,0xffa34234,0xffd99351,
        0xffe5e09e,0xff74b8b0,0xffb47aba,0xfff7f4e9
    };}
    static boolean sameMembers(int[] a,int[] b){
        int[] x=a.clone(),y=b.clone();Arrays.sort(x);Arrays.sort(y);return Arrays.equals(x,y);
    }
    static int[] portrait(int w,int h){
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float nx=(x+.5f)/w,ny=(y+.5f)/h;
            int r=(int)(20+215*nx),g=(int)(15+205*ny),b=(int)(17+164*(1-nx));
            if(x>w*.28&&x<w*.72&&y>h*.32&&y<h*.56){r/=4;g/=4;b/=4;}
            pixels[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return pixels;
    }
    static int geometryHash(StyleKit.Result r){
        int hash=1;
        for(StyleKit.Layer l:r.layers){
            hash=31*hash+l.loops.size();
            for(float[] path:l.loops)hash=31*hash+Arrays.hashCode(path);
        }
        return hash;
    }
    public static void main(String[] args){
        int[] base=source();
        final int[] styles={VinylStyleSuite.CONFETTI,VinylStyleSuite.PRISMATIC_DIAGONAL,
                            VinylStyleSuite.CHAINMAIL,VinylStyleSuite.LOW_POLY,
                            VinylStyleSuite.TOPOGRAPHIC,VinylStyleSuite.TORN_COLLAGE,
                            VinylStyleSuite.REDUCTION_WOODCUT,VinylStyleSuite.WOVEN_RIBBON};
        for(int style:styles)for(int count=2;count<=8;count++){
            int[] original=base.clone();
            int[] arranged=PaletteStylePolicy.arrange(PaletteStylePolicy.familyForNewStyle(style),base,count,0xfffaf9f7);
            ok(Arrays.equals(base,original),"Input swatches mutated for style "+style);
            ok(arranged.length==8 && sameMembers(arranged,base),"Lost or duplicated swatch for "+style+" count "+count);
            if(style==VinylStyleSuite.CONFETTI) {
                double min=Double.POSITIVE_INFINITY;
                for(int c:base)min=Math.min(min,ColorWorkspacePalette.lightness(c));
                ok(Math.abs(ColorWorkspacePalette.lightness(arranged[0])-min)<.01,"Confetti base is not darkest swatch");
            }
            if(style==VinylStyleSuite.PRISMATIC_DIAGONAL){
                double max=Double.NEGATIVE_INFINITY;
                for(int c:base)max=Math.max(max,ColorWorkspacePalette.lightness(c));
                ok(Math.abs(ColorWorkspacePalette.lightness(arranged[0])-max)<.01,"Prismatic underlay not lightest");
            }
            if(style==VinylStyleSuite.CONFETTI||style==VinylStyleSuite.PRISMATIC_DIAGONAL){
                int[] reversed=VinylPaletteMapping.forStyle(style,count,0xfffaf9f7,base,true,false);
                ok(reversed[0]==arranged[0],"Reversing palette moved solid foundation");
            }
            ok(new HashSet<Integer>(){{for(int c:arranged)add(c);}}.size()==8,"Duplicate physical colors");
        }
        for(int style:new int[]{VinylStyleSuite.CONFETTI,VinylStyleSuite.TEXT,VinylStyleSuite.GLASS}){
            ok(NewStyleLayerPolicy.isStructuralLayer(style,0),"Structural L1 is deletable for "+style);
            ok(!NewStyleLayerPolicy.canDelete(style,0,6),"Structural delete allowed for "+style);
            ok(NewStyleLayerPolicy.canDelete(style,1,6),"Cannot delete regular overlay for "+style);
        }
        ok(NewStyleLayerPolicy.isStructuralLayer(VinylStyleSuite.CHAINMAIL,0),"Chainmail scaffold not anchored");
        ok(!NewStyleLayerPolicy.canDelete(VinylStyleSuite.CONFETTI,1,2),"Deleted final confetti overlay");
        int[] image=portrait(90,112);
        VinylStyleSuite.Config narrow=new VinylStyleSuite.Config();
        narrow.style=VinylStyleSuite.LINOCUT;narrow.widthMm=120;
        narrow.detail=49;narrow.minimumMm=.254f;narrow.weightMm=.5f;
        StyleKit.Result a=VinylStyleSuite.generate(image,90,112,narrow);
        VinylStyleSuite.Config thick=narrow.copy();thick.weightMm=1.5f;
        StyleKit.Result b=VinylStyleSuite.generate(image,90,112,thick);
        ok(!a.layers.isEmpty()&&!b.layers.isEmpty(),"Linocut generated no paths");
        ok(geometryHash(a)!=geometryHash(b),"Linocut line-weight slider does not change output");
        System.out.println("PASS RC157 8-style tone/palette matrix, anchored layers, Linocut weight geometry");
    }
}

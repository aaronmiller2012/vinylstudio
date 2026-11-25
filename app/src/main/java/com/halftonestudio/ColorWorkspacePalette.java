package com.halftonestudio;

import java.util.Arrays;
/** Stateless and Android-independent palette rules shared by every artwork renderer. */
public final class ColorWorkspacePalette {
    private ColorWorkspacePalette(){}
    /** Exact layer counts, never implied extra empty swatches. */
    public static int columns(int count){
        if(count<1||count>9)throw new IllegalArgumentException("1 to 9 color slots");
        return count<=4?count:4;
    }
    /** Evenly sample all eight preset colors; avoid dropping later hues at smaller layer counts. */
    public static int[] select(int[] full,int count){
        if(full==null||full.length!=8||count<2||count>8)throw new IllegalArgumentException("Eight source colors, 2–8 layers");
        if(count==8)return full.clone();
        int[] selected=new int[count];
        for(int i=0;i<count;i++){
            // Choose anchor colors from the complete palette and retain original ordering.
            int sourceIndex=Math.round(i*7f/(count-1));
            selected[i]=full[sourceIndex];
        }
        return selected;
    }
    /**
     * Choose a deterministic, nested 2–8-color subset for a mounting surface.
     * A maximum-minimum-distance search in CIELAB selects visually different
     * existing vinyl swatches; it NEVER invents colors or changes channel IDs.
     * Returning more layers keeps all previously chosen swatches, adding only
     * the next most distinct available color. Actual stacking is then sorted
     * from nearest to furthest in lightness from the mounting surface.
     *
     * Use ONLY for preset selection. Saved/custom layers and real CMYK channel
     * separations must retain their own explicit physical order.
     */
    public static int[] selectForSurface(int[] full,int count,int surface){
        if(full==null||full.length!=8||count<2||count>8)
            throw new IllegalArgumentException("Eight palette colors and 2–8 active layers required");
        double[][] lab=new double[8][];
        // M4/D12: a photo with fewer real colours than slots may repeat a swatch.
        // A repeat has zero distinctness, so max-min selection takes it last; it is
        // never an error (558 threw "Duplicate source swatch" here).
        for(int i=0;i<8;i++)lab[i]=lab(full[i]);
        double[] paperLab=lab(surface);
        int[] order=new int[8];boolean[] used=new boolean[8];
        for(int k=0;k<count;k++){
            int winner=-1;double best=-1;
            for(int i=0;i<8;i++){
                if(used[i])continue;
                double paperDist=distance(lab[i],paperLab);
                double score=paperDist;
                if(k>0){
                    double nearest=Double.POSITIVE_INFINITY;
                    for(int prev=0;prev<k;prev++)
                        nearest=Math.min(nearest,distance(lab[i],lab[order[prev]]));
                    // Perceptual distinction dominates; slight substrate preference
                    // avoids nearly invisible low-contrast accents on dark/white paper.
                    score=nearest+.12*paperDist;
                    // An exact repeat of a chosen swatch adds no distinct sheet: take it only
                    // when nothing distinct remains (review F3: the paper bonus could pick it first).
                    if(nearest<1e-9)score=-1+1e-6*paperDist;
                }
                if(winner<0||score>best+1e-9){best=score;winner=i;}
            }
            order[k]=winner;used[winner]=true;
        }
        int[] result=new int[count];
        for(int i=0;i<count;i++)result[i]=full[order[i]];
        sortForSurface(result,count,surface);
        return result;
    }
    /** Keep all 8 original colors available in a stable unused tail. */
    public static int[] arrangedForSurface(int[] full,int count,int surface){
        int[] chosen=selectForSurface(full,count,surface),out=new int[8];
        System.arraycopy(chosen,0,out,0,count);
        // Track source slots by index, not value, so repeated swatches still fill all
        // eight slots (558 left transparent 0x00000000 tail slots for duplicates).
        boolean[] usedSlot=new boolean[8];
        for(int j=0;j<count;j++)for(int i=0;i<8;i++)if(!usedSlot[i]&&full[i]==out[j]){usedSlot[i]=true;break;}
        int cursor=count;
        for(int i=0;i<8;i++)if(!usedSlot[i])out[cursor++]=full[i];
        return out;
    }
    private static double[] lab(int rgb){
        double r=linear((rgb>>>16)&255),g=linear((rgb>>>8)&255),b=linear(rgb&255);
        double x=(.4124564*r+.3575761*g+.1804375*b)/.95047;
        double y=(.2126729*r+.7151522*g+.0721750*b);
        double z=(.0193339*r+.1191920*g+.9503041*b)/1.08883;
        double fx=labF(x),fy=labF(y),fz=labF(z);
        return new double[]{116*fy-16,500*(fx-fy),200*(fy-fz)};
    }
    private static double labF(double v){return v>216.0/24389.0?Math.cbrt(v):(24389.0/27*v+16.0)/116.0;}
    private static double distance(double[] a,double[] b){
        double x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];return Math.sqrt(x*x+y*y+z*z);
    }
    /** Arrange the n presented palette colors first; keep the rest uniquely available for future layers. */
    public static int[] arranged(int[] full,int count){
        if(full==null||full.length!=8||count<2||count>8)throw new IllegalArgumentException("Invalid preset palette");
        int[] chosen=select(full,count),out=new int[8];
        System.arraycopy(chosen,0,out,0,count);
        int j=count;
        for(int color:full){
            boolean used=false;
            for(int i=0;i<j;i++)if(out[i]==color){used=true;break;}
            if(!used&&j<8)out[j++]=color;
        }
        if(j!=8)throw new IllegalArgumentException("Preset palette must contain eight distinct colors");
        return out;
    }
    /** CIE L* lightness of an opaque sRGB swatch. Distinguishes brightness from hue. */
    public static double lightness(int rgb){
        double r=linear((rgb>>>16)&255),g=linear((rgb>>>8)&255),b=linear(rgb&255);
        double y=.2126*r+.7152*g+.0722*b;
        double f=y>216.0/24389.0?Math.cbrt(y):(24389.0/27.0*y+16.0)/116.0;
        return 116.0*f-16.0;
    }
    private static double linear(int channel){
        double v=channel/255.0;
        return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);
    }
    /**
     * Sort ONLY the chosen active colors from closest to substrate lightness to
     * most contrasting. Layer 1 is the physical bottom, last layer the top.
     * A pale mounting surface therefore uses light-to-dark, a dark one dark-to-light.
     * Stable for equal-tone colors. Mutates no inactive/parked swatches.
     * Apply when the user chooses a PRESET, not when loading an existing project
     * or changing a custom vinyl swatch. Never change CMYK channel identities.
     */
    public static void sortForSurface(int[] colors,int activeCount,int paper){
        if(colors==null||activeCount<0||activeCount>colors.length)throw new IllegalArgumentException("Invalid stack");
        double substrateL=lightness(paper);
        for(int i=1;i<activeCount;i++){
            int picked=colors[i];
            double diff=Math.abs(lightness(picked)-substrateL);
            int j=i-1;
            while(j>=0&&Math.abs(lightness(colors[j])-substrateL)>diff+1e-9){
                colors[j+1]=colors[j];j--;
            }
            colors[j+1]=picked;
        }
    }
    /** Choose a visually distinct new color while leaving all existing swatches untouched. */
    public static int nextUnusedColor(int[] pool,int[] alreadyUsed){
        if(pool==null||pool.length==0)throw new IllegalArgumentException("Palette is empty");
        int best=pool[0];long bestDistance=-1;
        for(int candidate:pool){
            boolean same=false;for(int used:alreadyUsed)if(candidate==used){same=true;break;}
            if(same)continue;
            long closest=Long.MAX_VALUE;
            for(int used:alreadyUsed){
                long dr=((candidate>>>16)&255)-((used>>>16)&255);
                long dg=((candidate>>>8)&255)-((used>>>8)&255);
                long db=(candidate&255)-(used&255);
                closest=Math.min(closest,2*dr*dr+4*dg*dg+2*db*db);
            }
            if(closest>bestDistance){bestDistance=closest;best=candidate;}
        }
        return best;
    }
    public static final String[] TESS_NAMES={"Studio Portrait","Illuminated Portrait","Balanced Eight","Vivid","Ocean","Vintage","CMYK"};
    private static final int[][] TESS_COLORS={
        {0xff20252c,0xff3b4565,0xff24727c,0xffb34e45,0xffefad76,0xfff4db9b,0xffdfa5bd,0xff63753b},
        {0xff252437,0xff70677e,0xfff0cf6f,0xfff6ac8b,0xfff16c64,0xff7dd8b2,0xff72b8e8,0xffb9a0ed},
        {0xff252735,0xffa84f2c,0xffebc86d,0xff287a60,0xff22839b,0xffa8c7eb,0xff7953a9,0xffe8a2c3},
        {0xff191b2a,0xff6e3d9e,0xffc85088,0xfff09d57,0xfff1cf6b,0xff2c84b8,0xff36c1b8,0xffecdede},
        {0xff162c40,0xff235276,0xff3687ad,0xff70b9bf,0xffc9eddf,0xffd38b71,0xff7865a5,0xfff8eee0},
        {0xff2a2c32,0xff794342,0xffc46b52,0xffdfa777,0xffe9bf77,0xff537d77,0xffa0787b,0xfffff6e9},
        {0xff181818,0xffdf0077,0xff00a4db,0xffffdd00,0xfff3e4ca,0xff854bb9,0xfff06d46,0xff39aa91}
    };
    public static int[] tessPalette(int index){
        if(index<0||index>=TESS_COLORS.length)throw new IllegalArgumentException("Palette index");
        return TESS_COLORS[index].clone();
    }
}

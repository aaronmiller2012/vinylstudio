package com.halftonestudio;

import java.util.Arrays;

/**
 * Shared, deterministic photo-to-opaque-vinyl color ownership.
 *
 * This class only selects an existing physical slot. It never creates colors,
 * mutates or reorders swatches, alters the crop/Snip, or emits cut geometry.
 * Mono linework and CMYK subtractive channel separations deliberately do not
 * use this classifier: their strengths encode different physical quantities.
 */
public final class PhotoSwatchAssociation {
    public static final int FACETS=0, PANES=1, SHAPES=2, REGIONS=3,
        STENCIL=4, ORNAMENTS=5, TEXTILES=6;
    private final int[] palette;
    private final double[][] lab;
    private final boolean[] enabled;
    private final int[] lightRank;
    private final double lightWeight, chromaWeight, adapt;
    private final double photoLow, photoHigh, paletteLow, paletteHigh;
    private final boolean normalize;
    private final int[] cache=new int[32768];
    private byte[][] rankCache;

    public PhotoSwatchAssociation(int[] colors, int count, boolean[] active,
                                  int[] source, boolean[] valid, int profile) {
        if(colors==null||count<1||count>colors.length ||
           (valid!=null&&(source==null||valid.length!=source.length)))
            throw new IllegalArgumentException("Invalid photo/palette dimensions");
        palette=Arrays.copyOf(colors,count);
        lab=new double[count][];enabled=new boolean[count];
        double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        boolean any=false;
        for(int i=0;i<count;i++){
            enabled[i]=active==null||(i<active.length&&active[i]);
            if(!enabled[i])continue;
            any=true;lab[i]=PerceptualVinylColor.lab(palette[i]);
            lo=Math.min(lo,lab[i][0]);hi=Math.max(hi,lab[i][0]);
        }
        if(!any)throw new IllegalArgumentException("No enabled swatches");
        paletteLow=lo;paletteHigh=hi;
        Integer[] order=new Integer[count];for(int i=0;i<count;i++)order[i]=i;
        Arrays.sort(order,(a,b)->Double.compare(lab[a]==null?Double.POSITIVE_INFINITY:lab[a][0],lab[b]==null?Double.POSITIVE_INFINITY:lab[b][0]));
        lightRank=new int[count];for(int i=0;i<count;i++)lightRank[order[i]]=i;
        switch(profile){
            case PANES: lightWeight=1.85;chromaWeight=1.04;adapt=.30;break;
            case SHAPES: lightWeight=1.68;chromaWeight=1.18;adapt=.22;break;
            case STENCIL: lightWeight=1.80;chromaWeight=1.06;adapt=.26;break;
            case TEXTILES: lightWeight=1.95;chromaWeight=1.03;adapt=.30;break;
            case ORNAMENTS: lightWeight=1.74;chromaWeight=1.14;adapt=.25;break;
            case REGIONS: lightWeight=1.77;chromaWeight=1.12;adapt=.27;break;
            default: lightWeight=1.72;chromaWeight=1.14;adapt=.25;break;
        }
        int[] hist=new int[256];int n=0;
        if(source!=null){
            int stride=Math.max(1,source.length/12000);
            for(int i=0;i<source.length;i+=stride){
                if((i&8191)==0)CancellationCheckpoint.check();
                if((valid!=null&&!valid[i])||(source[i]>>>24)<128)continue;
                int bucket=(int)Math.round(PerceptualVinylColor.lab(source[i])[0]*255);
                hist[Math.max(0,Math.min(255,bucket))]++;n++;
            }
        }
        photoLow=percentile(hist,n,.04);photoHigh=percentile(hist,n,.96);
        // Flat sources or same-lightness palettes should not acquire artificial contrast.
        normalize=n>32&&photoHigh-photoLow>.16&&paletteHigh-paletteLow>.16;
        Arrays.fill(cache,-1);
    }
    public PhotoSwatchAssociation(int[] colors,int count,boolean[] active,
                                  StyleKit.Source src,int profile){
        this(colors,count,active,src==null?null:src.argb,
             src==null?null:src.valid,profile);
    }
    private static double percentile(int[] h,int n,double pct){
        if(n==0)return 0;
        int target=(int)Math.floor((n-1)*pct),seen=0;
        for(int i=0;i<h.length;i++){seen+=h[i];if(seen>target)return i/255d;}
        return 1;
    }
    public int choose(int argb){return choose(argb,-1,0);}

    /**
     * Optional tonal neighborhood: restrict output to palette slots whose
     * lightness ranks differ from the desired rank by at most `radius`.
     * Used by engines whose spatial geometry relies on a continuous tone ramp.
     */
    public int chooseNearRank(int argb,int desiredRank,int radius){
        return choose(argb,desiredRank,Math.max(0,radius));
    }
    private int choose(int argb,int rank,int radius){
        // Exact edits have absolute priority and must retain their physical index.
        for(int i=0;i<palette.length;i++)if(enabled[i] && (rank<0||Math.abs(lightRank[i]-rank)<=radius) &&
            (argb&0xffffff)==(palette[i]&0xffffff))return i;
        double[] source=PerceptualVinylColor.lab(argb);
        if(normalize){
            double wanted=paletteLow+(paletteHigh-paletteLow)*
                Math.max(0,Math.min(1,(source[0]-photoLow)/(photoHigh-photoLow)));
            source[0]=Math.max(0,Math.min(1,source[0]+
                Math.max(-.065,Math.min(.065,(wanted-source[0])*adapt))));
        }
        int best=-1;double bestDist=Double.POSITIVE_INFINITY;
        for(int i=0;i<lab.length;i++)if(enabled[i]){
            if(rank>=0&&Math.abs(lightRank[i]-rank)>radius)continue;
            double[] c=lab[i];double dl=source[0]-c[0],da=source[1]-c[1],db=source[2]-c[2];
            double score=lightWeight*dl*dl+chromaWeight*(da*da+db*db);
            // Do not assign a strongly colored photograph to the opposite hue
            // solely to achieve an appealing lightness match.
            double srcChroma=Math.hypot(source[1],source[2]);
            double vinylChroma=Math.hypot(c[1],c[2]);
            if(srcChroma>.065&&vinylChroma>.075){
                double cos=(source[1]*c[1]+source[2]*c[2])/(srcChroma*vinylChroma);
                if(cos<-.15)score+=(Math.min(.030,srcChroma*vinylChroma))*(-.15-cos);
            }
            if(score<bestDist){bestDist=score;best=i;}
        }
        // A degenerate tone neighborhood may exclude the only enabled slot.
        if(best<0&&rank>=0)return choose(argb,-1,0);
        return best;
    }

    /** Quantized cache for raster-only adjacent-rank matching. Exact eligible
     * swatches retain priority. Cache is local to one prepared render. */
    int chooseNearRankCached(int argb,int rank){
        for(int i=0;i<palette.length;i++)if(enabled[i]&&Math.abs(lightRank[i]-rank)<=1&&
            (argb&0xffffff)==(palette[i]&0xffffff))return i;
        if(rankCache==null)rankCache=new byte[palette.length][];
        if(rankCache[rank]==null)rankCache[rank]=new byte[32768];
        int key=(((argb>>>19)&31)<<10)|(((argb>>>11)&31)<<5)|((argb>>>3)&31);
        int hit=rankCache[rank][key]&255;if(hit>0)return hit-1;
        int r=((key>>>10)&31)*255/31,g=((key>>>5)&31)*255/31,b=(key&31)*255/31;
        int selected=choose(0xff000000|(r<<16)|(g<<8)|b,rank,1);
        rankCache[rank][key]=(byte)(selected+1);return selected;
    }

    /** Fast quantized 5/5/5 approximation for full-raster segmentation only.
     * Exact swatch comparisons still run against the actual RGB first. */
    public int chooseCached(int argb){
        for(int i=0;i<palette.length;i++)if(enabled[i] &&
            (argb&0xffffff)==(palette[i]&0xffffff))return i;
        int key=(((argb>>>19)&31)<<10)|(((argb>>>11)&31)<<5)|((argb>>>3)&31);
        int hit=cache[key];if(hit>=0)return hit;
        int r=((key>>>10)&31)*255/31,g=((key>>>5)&31)*255/31,b=(key&31)*255/31;
        return cache[key]=choose(0xff000000|(r<<16)|(g<<8)|b);
    }
}

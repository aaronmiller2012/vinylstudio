package com.halftonestudio;

/** Nondestructive image-tone preparation for opaque cut-vinyl separations.
 * All controls change only a temporary pixel copy; the original photo and snips stay intact.
 * The nearest-neighbor mask is applied to determine which pixels are eligible for editing;
 * the generator still enforces the true cuttable SnipMask geometry during export.
 */
final class ImageLevelsReference {
    private ImageLevelsReference(){}
    private static float clip(float v){return Math.max(0f,Math.min(1f,v));}
    private static int byteClip(float v){return Math.max(0,Math.min(255,Math.round(v)));}
    private static float luma(int p){return (((p>>16)&255)*.2126f+((p>>8)&255)*.7152f+(p&255)*.0722f)/255f;}
    private static boolean included(VinylEngine.Settings s,int x,int y,int w,int h){
        return s.snips==null||s.snips.isEmpty()||s.snips.allows((x+.5f)/w,(y+.5f)/h);
    }
    public static boolean isNeutral(VinylEngine.Settings s){
        return s.levelsBlack==0&&s.levelsWhite==255&&Math.abs(s.levelsGamma-1f)<.0001f
            &&Math.abs(s.levelsShadows)<.0001f&&Math.abs(s.levelsHighlights)<.0001f
            &&s.edgePriority<.0001f&&Math.abs(s.exposureStops)<.0001f
            &&Math.abs(s.photoContrast-1f)<.0001f;
    }
    /** Returns a distinct RGB array. Uncut locations are unchanged. */
    public static int[] apply(int[] source,int w,int h,VinylEngine.Settings s){
        if(source==null||source.length!=w*h||w<=0||h<=0)throw new IllegalArgumentException("Invalid image");
        if(isNeutral(s))return source;
        int[] result=source.clone();
        float min=s.levelsBlack/255f,max=s.levelsWhite/255f;
        if(min>=max)return result;
        float[] yplane=new float[source.length];
        for(int i=0;i<source.length;i++)yplane[i]=luma(source[i]);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            if(x==0 && Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Superseded lighting render");
            int i=y*w+x,p=source[i];
            if(!included(s,x,y,w,h))continue;
            float original=yplane[i];
            float base=(float)Math.pow(clip((original-min)/(max-min)),1f/s.levelsGamma);
            base=clip((float)(base*Math.pow(2d,s.exposureStops)));
            base=clip((base-.5f)*s.photoContrast+.5f);
            float dark=(1-base)*(1-base),bright=base*base;
            float adjusted=clip(base+s.levelsShadows*.28f*dark+s.levelsHighlights*.28f*bright);
            if(s.edgePriority>.0001f){
                float sum=0f,maxDifference=0f;int n=0;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                    int nx=Math.max(0,Math.min(w-1,x+dx)),ny=Math.max(0,Math.min(h-1,y+dy));
                    float neighbor=included(s,nx,ny,w,h)?yplane[ny*w+nx]:original;
                    sum+=neighbor;n++;maxDifference=Math.max(maxDifference,Math.abs(original-neighbor));
                }
                float detail=original-sum/n;
                float emphasis=clip(maxDifference*5f);
                adjusted=clip(adjusted+s.edgePriority*detail*(.55f+.90f*emphasis));
            }
            float delta=(adjusted-original)*255f;
            int a=(p>>>24)&255;
            int rr=byteClip(((p>>>16)&255)+delta),gg=byteClip(((p>>>8)&255)+delta),bb=byteClip((p&255)+delta);
            result[i]=(a<<24)|(rr<<16)|(gg<<8)|bb;
        }
        return result;
    }
    /** Percentile-driven automatic points, measured only in retained snip regions. */
    public static int[] autoPoints(int[] src,int w,int h,VinylEngine.Settings s){
        int[] bins=new int[256];int n=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(included(s,x,y,w,h)){
            int bin=Math.max(0,Math.min(255,Math.round(luma(src[y*w+x])*255f)));
            bins[bin]++;n++;
        }
        if(n==0)return new int[]{0,255};
        int lowTarget=(int)(n*.01),highTarget=(int)(n*.99),cum=0,low=0,high=255;
        for(int v=0;v<256;v++){cum+=bins[v];if(cum>=lowTarget){low=v;break;}}
        cum=0;for(int v=0;v<256;v++){cum+=bins[v];if(cum>=highTarget){high=v;break;}}
        if(high-low<24)return new int[]{0,255};
        return new int[]{low,high};
    }
}

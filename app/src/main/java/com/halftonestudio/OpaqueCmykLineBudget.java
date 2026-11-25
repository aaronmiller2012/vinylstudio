package com.halftonestudio;

/**
 * Opaque-vinyl line coverage budget. Standard CMYK ink screens assume that
 * translucent inks mix where they cross. On four opaque sheets, the TOP vinyl
 * hides everything below it: independently lifting four screens produces a
 * nearly solid dark face even when every individual ribbon is cuttable.
 *
 * Bound the SUM of projected physical ribbon widths by source luminance, not
 * by an arbitrary channel-specific cutoff. All four channels stay eligible,
 * and darker facial detail retains greater available coverage. This class
 * changes no cutter geometry, shapes, masks, palettes or crop coordinates.
 * The caller applies the common scale to the final ribbon half-width.
 */
final class OpaqueCmykLineBudget {
    private OpaqueCmykLineBudget() {}

    static boolean eligible(VinylEngine.Settings s,int style) {
        if (!s.opaqueLineBalance || s.classicCmykLines || s.lineInvert ||
            s.mode!=VinylEngine.CMYK || s.linePresenceRecovery<=.001f) return false;
        if (!(style==VinylEngine.LINE || style==VinylEngine.STRAIGHT ||
              style==VinylEngine.CONTOUR || style==VinylEngine.WAVE ||
              style==VinylEngine.HATCH || style==VinylEngine.CROSSHATCH ||
              style==VinylEngine.ZIGZAG)) return false;
        int p=s.separationBackground;
        if (((p>>16)&255)<238 || ((p>>8)&255)<238 || (p&255)<238) return false;
        boolean[] seen=new boolean[4];
        for(VinylEngine.Layer l:s.layers) {
            if(!l.enabled || l.excludeFromExport || l.silhouetteBase)continue;
            if(l.cmykChannel>=0 && l.cmykChannel<4)seen[l.cmykChannel]=true;
        }
        return seen[0]&&seen[1]&&seen[2]&&seen[3];
    }

    private static float clamp(float v){return Math.max(0f,Math.min(1f,v));}

    /** Scale is computed once per source pixel, reused across four rotated
     * carrier families. One float/pixel is temporary and discarded before the
     * render Result reaches the UI (no long-lived bitmap-sized buffer).
     */
    static float[] prepare(int[] pixels,int w,int h,VinylEngine.Settings s) {
        float[] scale=new float[pixels.length];
        for(int pos=0;pos<pixels.length;pos++) {
            if((pos&4095)==0 && Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException();
            int pixel=pixels[pos],alpha=(pixel>>>24)&255;
            if(alpha<128){scale[pos]=1f;continue;}
            // Composite on the ACTUAL mounting surface, not hardcoded white.
            int r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;
            if(alpha<255){
                int paper=s.separationBackground,back=255-alpha;
                r=(r*alpha+((paper>>16)&255)*back)/255;
                g=(g*alpha+((paper>>8)&255)*back)/255;
                b=(b*alpha+(paper&255)*back)/255;
            }
            float luminance=(.2126f*r+.7152f*g+.0722f*b)/255f;
            // Width fractions for the unbounded RC101 transfer, after its
            // midtone lift and legal maximum width. Use the selected angle/
            // style-independent conservative upper bound, including overrides.
            float predicted=0;
            for(int i=0;i<s.layers.size();i++) {
                VinylEngine.Layer l=s.layers.get(i);
                if(!l.enabled||l.excludeFromExport||l.cmykChannel<0||l.cmykChannel>3)continue;
                float c=VinylEngine.coverage(pixel,s,i);
                if(c<=.008f)continue;
                float lifted=c+s.portraitMidtones*((float)Math.sqrt(c)-c);
                float gamma=l.lineGammaOverride>0?l.lineGammaOverride:s.lineGamma;
                float effective=Math.max(.40f,gamma*(1f-.48f*Math.max(s.linePresenceRecovery,s.lineHighlightRetention)));
                float width=(float)Math.pow(clamp(lifted),effective)*s.lineMaxFraction*l.lineWidthScale;
                predicted+=Math.min(.92f,width);
            }
            // A light skin pixel must keep paper visible. Dark hair, eyebrows,
            // pupils and beard get progressively more space for full CMYK.
            // No absolute floor is imposed on ANY individual channel.
            float target=.10f+2.60f*(float)Math.pow(clamp(1f-luminance),1.4f);
            scale[pos]=predicted>.0001f?Math.min(1f,target/predicted):1f;
        }
        return scale;
    }

    /** Local luminance contrast is the missing facial-detail signal. A broad
     * 9mm feature scale compares a pixel with its nearby portrait, not the
     * whole photograph; it makes dark eye/lip creases stronger and nose/cheek
     * highlights lighter without changing the underlying photo or Snip.
     * O(w*h) summed-area construction replaces repeated neighborhood sampling.
     */
    static float[] localDetail(int[] pixels,int w,int h,VinylEngine.Settings s) {
        final int stride=w+1;
        final long[] light=new long[(w+1)*(h+1)];
        final int[] valid=new int[(w+1)*(h+1)];
        float[] lum=new float[pixels.length];
        for(int y=1;y<=h;y++) {
            if((y&31)==0&&Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException();
            long rowSum=0;int rowCount=0;
            for(int x=1;x<=w;x++) {
                int p=pixels[(y-1)*w+x-1],a=(p>>>24)&255;
                float v=(.2126f*((p>>16)&255)+.7152f*((p>>8)&255)+.0722f*(p&255))/255f;
                lum[(y-1)*w+x-1]=v;
                if(a>=128){rowSum+=Math.round(v*1024);rowCount++;}
                int id=y*stride+x;
                light[id]=light[id-stride]+rowSum;
                valid[id]=valid[id-stride]+rowCount;
            }
        }
        // Radius scales with PHYSICAL artwork dimensions, not image DPI.
        int rx=Math.max(2,Math.round(9f*w/s.widthMm));
        int ry=Math.max(2,Math.round(9f*h/(s.widthMm*h/(float)w)));
        float[] factor=new float[pixels.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int id=y*w+x;
            if((pixels[id]>>>24)<128){factor[id]=1f;continue;}
            int xa=Math.max(0,x-rx),xb=Math.min(w-1,x+rx);
            int ya=Math.max(0,y-ry),yb=Math.min(h-1,y+ry);
            int a=ya*stride+xa,b=ya*stride+xb+1,c=(yb+1)*stride+xa,d=(yb+1)*stride+xb+1;
            int n=valid[d]-valid[b]-valid[c]+valid[a];
            float average=n>=5?(light[d]-light[b]-light[c]+light[a])/(1024f*n):lum[id];
            // A restrained high-pass response: no false edge is created at
            // alpha cutouts, smooth regions stay exactly at 1, and saturation
            // is limited to protect per-lane physical cut corridors.
            float delta=average-lum[id];
            factor[id]=Math.max(.60f,Math.min(1.45f,1f+2.6f*delta));
        }
        return factor;
    }

}

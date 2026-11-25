package com.halftonestudio;

import java.util.*;
/** Bounded diagnostic of preview dots; export must separately validate exported contours. */
public final class StipplePreflight {
    private StipplePreflight() {}
    public static final class Report {
        public final int[] counts;public final int undersized,tooClose,overSvgLimit;
        public final float smallestMm;
        Report(int[] n,int u,int t,int o,float s){counts=n;undersized=u;tooClose=t;overSvgLimit=o;smallestMm=s;}
        public String summary(){return "Stipple dot envelopes: min "+String.format(Locale.US,"%.2f",smallestMm)+
            " mm · under minimum "+undersized+" · close pairs "+tooClose+" · SVG layer limit warnings "+overSvgLimit+
            ". Advisory: final clipped polygons may differ.";}
    }
    public static Report inspect(MultiColorStippleEngine.Result r,float minFeatureMm,float sameSheetGapMm){
        if(r==null)throw new IllegalArgumentException("No stipple geometry");
        @SuppressWarnings("unchecked") // generic array creation; every slot is filled below
        ArrayList<MultiColorStippleEngine.Dot>[] layers=(ArrayList<MultiColorStippleEngine.Dot>[])new ArrayList<?>[r.options.layerCount];
        for(int i=0;i<layers.length;i++)layers[i]=new ArrayList<>();
        float mmPerPx=r.options.widthMm/r.width;
        int under=0;float smallest=Float.POSITIVE_INFINITY;
        for(MultiColorStippleEngine.Dot d:r.dots){
            if(d.layer<0||d.layer>=layers.length)continue;
            layers[d.layer].add(d);
            float diameter=2*d.r*mmPerPx;
            smallest=Math.min(smallest,diameter);
            if(diameter+1e-4f<minFeatureMm)under++;
        }
        int[] counts=new int[layers.length];int close=0,over=0;
        for(int li=0;li<layers.length;li++){
            ArrayList<MultiColorStippleEngine.Dot> dots=layers[li];counts[li]=dots.size();
            if(dots.size()+4>5000)over++;
            // Sweep sorted by X, rather than quadratic all-pairs over every layer.
            dots.sort(Comparator.comparingDouble(d->d.x));
            float maxRadius=0;for(MultiColorStippleEngine.Dot d:dots)maxRadius=Math.max(maxRadius,d.r);
            float gapPx=Math.max(0,sameSheetGapMm/mmPerPx);
            for(int i=0;i<dots.size();i++){
                MultiColorStippleEngine.Dot a=dots.get(i);
                for(int j=i+1;j<dots.size();j++){
                    MultiColorStippleEngine.Dot b=dots.get(j);
                    if(b.x-a.x>a.r+maxRadius+gapPx)break;
                    float req=a.r+b.r+gapPx;
                    float dx=a.x-b.x,dy=a.y-b.y;
                    if(dx*dx+dy*dy<(req-0.02f)*(req-0.02f))close++;
                }
            }
        }
        return new Report(counts,under,close,over,Float.isFinite(smallest)?smallest:0f);
    }
}

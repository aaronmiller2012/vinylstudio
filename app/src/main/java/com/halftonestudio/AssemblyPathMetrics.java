package com.halftonestudio;

import android.graphics.Path;
import android.graphics.PathMeasure;

/** Approximate authored vector length and contour counts from the same immutable
 * artwork paths used by the assembly document. No union, no classification of
 * hole topology and no registration marks. Runs on the document worker only. */
final class AssemblyPathMetrics {
    private static final int MAX_CONTOURS=250000;
    private AssemblyPathMetrics(){}
    static ArtworkCutMetrics.Stats measure(double scale,Path... paths){
        if(!Double.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Invalid physical scale");
        long closed=0,open=0;double length=0;
        for(Path path:paths){
            CancellationCheckpoint.check();
            if(path==null)throw new IllegalArgumentException("Missing artwork path");
            if(path.isEmpty())continue;
            PathMeasure measure=new PathMeasure(path,false);
            int visited=0;
            do{
                CancellationCheckpoint.check();
                if(++visited>MAX_CONTOURS||closed+open>=MAX_CONTOURS)throw new IllegalStateException("Contour analysis budget exceeded");
                float value=measure.getLength();
                if(!Float.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid contour length");
                if(value==0)continue; // Move-only / degenerate contour is not a toolpath.
                if(measure.isClosed())closed++;else open++;
                length+=value*scale;
            }while(measure.nextContour());
        }
        return ArtworkCutMetrics.pathSummary(closed,open,length);
    }
}

package com.halftonestudio;

import android.graphics.Path;
import java.util.Arrays;

/** Draw-only cache. Geometry/export still read SnipMask directly. Main thread only. */
final class SnipCanvasPathCache {
    private SnipMask owner;
    private int fingerprint;
    private float width=Float.NaN,height=Float.NaN;
    private Path[] outlines=new Path[0];
    Path[] paths(SnipMask snips,float w,float h){
        if(snips==null||snips.isEmpty())return new Path[0];
        int hash=1;
        for(SnipMask.Operation op:snips.operations()){
            hash=31*hash+op.kind;
            hash=31*hash+Arrays.hashCode(op.xy);
        }
        if(owner==snips&&hash==fingerprint&&w==width&&h==height&&outlines.length==snips.size())
            return outlines;
        Path[] rebuilt=new Path[snips.size()];int n=0;
        for(SnipMask.Operation op:snips.operations()){
            Path path=new Path();
            for(int i=0;i<op.xy.length;i+=2){
                float x=op.xy[i]*w,y=op.xy[i+1]*h;
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            path.close();rebuilt[n++]=path;
        }
        owner=snips;fingerprint=hash;width=w;height=h;outlines=rebuilt;
        return rebuilt;
    }
    void clear(){owner=null;outlines=new Path[0];}
}

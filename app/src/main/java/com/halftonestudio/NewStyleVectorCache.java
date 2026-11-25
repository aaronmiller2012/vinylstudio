package com.halftonestudio;

import android.graphics.Path;

/** Immutable paths prepared on the render worker. Same mm geometry used by SVG export. */
final class NewStyleVectorCache {
    final StyleKit.Result source;
    final Path[] fills,strokes;
    final int[] colors;
    final float[] strokeWidths;
    NewStyleVectorCache(StyleKit.Result value){
        source=value;int n=value.layers.size();
        fills=new Path[n];strokes=new Path[n];colors=new int[n];strokeWidths=new float[n];
        for(int i=0;i<n;i++){
            StyleKit.Layer layer=value.layers.get(i);colors[i]=layer.color;
            strokeWidths[i]=layer.strokeMm;
            Path filled=new Path();filled.setFillType(Path.FillType.EVEN_ODD);
            Path drawn=new Path();
            for(float[] loop:layer.loops){
                if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                if(loop.length<6)continue;
                filled.moveTo(loop[0],loop[1]);
                for(int j=2;j<loop.length;j+=2)filled.lineTo(loop[j],loop[j+1]);
                filled.close();
            }
            for(float[] line:layer.lines){
                if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
                if(line.length<4)continue;
                drawn.moveTo(line[0],line[1]);
                for(int j=2;j<line.length;j+=2)drawn.lineTo(line[j],line[j+1]);
            }
            fills[i]=filled;strokes[i]=drawn;
        }
    }
}

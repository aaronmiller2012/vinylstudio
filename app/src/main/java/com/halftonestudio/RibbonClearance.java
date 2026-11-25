package com.halftonestudio;

import java.util.Arrays;
/** Finished-ribbon centerline clearance validation. No Android dependencies.
 * Uses a uniform spatial grid (segment bounding boxes) and exact segment
 * intersection/distance; skips only directly adjacent shared-endpoint edges.
 * This is conservative: uncertain geometries fail closed before SVG export.
 */
public final class RibbonClearance {
    private RibbonClearance(){}
    private static final int MAX_CELLS=1_500_000,MAX_ENTRIES=8_000_000;
    public static void verify(float[] xy,float physicalScale,float[] radiiPx,float pixelMm,
                              float weedGapMm,float maxWidthMm){
        if(xy==null||radiiPx==null||xy.length/2!=radiiPx.length||xy.length<4||
           physicalScale<=0||pixelMm<=0||weedGapMm<0||maxWidthMm<=0)
            throw new IllegalArgumentException("Invalid ribbon clearance geometry");
        int count=radiiPx.length-1;
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        for(int i=0;i<xy.length;i+=2){
            float x=xy[i]*physicalScale,y=xy[i+1]*physicalScale;
            if(!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalArgumentException("Nonfinite ribbon centerline");
            minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
        }
        float cell=Math.max(.5f,maxWidthMm+weedGapMm);
        int columns=(int)Math.floor((maxX-minX)/cell)+2;
        int rows=(int)Math.floor((maxY-minY)/cell)+2;
        if((long)columns*rows>MAX_CELLS)throw new IllegalArgumentException("Ribbon clearance grid exceeds safety budget; reduce size or depth");
        int[] heads=new int[columns*rows];Arrays.fill(heads,-1);
        int[] linkedSegment=new int[Math.min(MAX_ENTRIES,Math.max(1024,count*8))];
        int[] next=new int[linkedSegment.length];
        int[] lastSeen=new int[Math.max(1,count)];Arrays.fill(lastSeen,-1);
        int used=0;
        for(int seg=0;seg<count;seg++){
            if((seg&255)==0&&Thread.currentThread().isInterrupted())throw new IllegalStateException("Cancelled ribbon clearance check");
            float ax=xy[seg*2]*physicalScale,ay=xy[seg*2+1]*physicalScale;
            float bx=xy[(seg+1)*2]*physicalScale,by=xy[(seg+1)*2+1]*physicalScale;
            int left=cellX(Math.min(ax,bx)-cell,minX,cell,columns);
            int right=cellX(Math.max(ax,bx)+cell,minX,cell,columns);
            int top=cellX(Math.min(ay,by)-cell,minY,cell,rows);
            int bottom=cellX(Math.max(ay,by)+cell,minY,cell,rows);
            float thisRadius=Math.max(radiiPx[seg],radiiPx[seg+1])*pixelMm;
            for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
                for(int entry=heads[y*columns+x];entry>=0;entry=next[entry]){
                    int other=linkedSegment[entry];
                    if(other>=seg-1||lastSeen[other]==seg)continue;
                    lastSeen[other]=seg;
                    float otherRadius=Math.max(radiiPx[other],radiiPx[other+1])*pixelMm;
                    // Account for worst-case occupancy-grid quantization on
                    // both sides of the ribbon before allowing a cut export.
                    float required=thisRadius+otherRadius+weedGapMm+1.4143f*pixelMm;
                    if(required<=0)continue;
                    float cx=xy[other*2]*physicalScale,cy=xy[other*2+1]*physicalScale;
                    float dx=xy[(other+1)*2]*physicalScale,dy=xy[(other+1)*2+1]*physicalScale;
                    if(distanceSquared(ax,ay,bx,by,cx,cy,dx,dy)+1e-6f<required*required)
                        throw new IllegalArgumentException("Ribbon paths are too close after photographic folds (segments "
                            +(other+1)+" and "+(seg+1)+"). Reduce depth/folding or ribbon width, increase artwork size, or lower weed gap only after a test cut.");
                }
            }
            // Store this segment's exact bounding cells. The next segment's
            // expanded query finds any earlier edge within maxWidth + weedGap.
            left=cellX(Math.min(ax,bx),minX,cell,columns);
            right=cellX(Math.max(ax,bx),minX,cell,columns);
            top=cellX(Math.min(ay,by),minY,cell,rows);
            bottom=cellX(Math.max(ay,by),minY,cell,rows);
            for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){
                if(used>=MAX_ENTRIES)throw new IllegalArgumentException("Ribbon clearance index exceeds processing budget");
                if(used==linkedSegment.length){
                    int size=Math.min(MAX_ENTRIES,linkedSegment.length*2);
                    linkedSegment=Arrays.copyOf(linkedSegment,size);next=Arrays.copyOf(next,size);
                }
                int cellIdx=y*columns+x;
                linkedSegment[used]=seg;next[used]=heads[cellIdx];heads[cellIdx]=used++;
            }
        }
    }
    private static int cellX(float value,float zero,float step,int size){
        return Math.max(0,Math.min(size-1,(int)Math.floor((value-zero)/step)));
    }
    private static float pointSegment(float px,float py,float ax,float ay,float bx,float by){
        float dx=bx-ax,dy=by-ay,len=dx*dx+dy*dy;
        float t=len<1e-12f?0:Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/len));
        float x=px-(ax+dx*t),y=py-(ay+dy*t);return x*x+y*y;
    }
    private static float cross(float ax,float ay,float bx,float by,float cx,float cy){
        return (bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
    }
    private static float distanceSquared(float ax,float ay,float bx,float by,
                                         float cx,float cy,float dx,float dy){
        float a=cross(ax,ay,bx,by,cx,cy),b=cross(ax,ay,bx,by,dx,dy);
        float c=cross(cx,cy,dx,dy,ax,ay),d=cross(cx,cy,dx,dy,bx,by);
        if(a*b<0&&c*d<0)return 0f;
        return Math.min(Math.min(pointSegment(ax,ay,cx,cy,dx,dy),pointSegment(bx,by,cx,cy,dx,dy)),
            Math.min(pointSegment(cx,cy,ax,ay,bx,by),pointSegment(dx,dy,ax,ay,bx,by)));
    }
}

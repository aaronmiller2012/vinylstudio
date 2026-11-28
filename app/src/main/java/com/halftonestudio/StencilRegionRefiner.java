package com.halftonestudio;

import java.util.Arrays;

/**
 * Cut-aware, exclusive multi-label segmentation cleanup.
 *
 * Important: operate on the single label map, not each layer independently.
 * A removed fleck is reassigned to an adjacent physical color instead of
 * creating a transparent/white hole. Invalid snipped pixels are never filled.
 * All work is deterministic, bounded, and independent of Android framework.
 */
public final class StencilRegionRefiner {
    private StencilRegionRefiner() { }

    public static final class Output {
        public final int[] owner;
        public final int changedPixels, smallRegionsMerged;
        Output(int[] labels, int changed, int merged) {
            this.owner = labels; this.changedPixels=changed; this.smallRegionsMerged=merged;
        }
    }

    public static Output refine(int[] source, boolean[] allowed, int w, int h,
                                int colors, float detail, int minimumAreaPixels) {
        if(source==null||allowed==null||w<=0||h<=0||(long)w*h!=source.length
                ||allowed.length!=source.length||colors<2||colors>8
                ||!Float.isFinite(detail)||detail<0f||detail>1f
                ||minimumAreaPixels<0)
            throw new IllegalArgumentException("Invalid stencil region refinement");
        int[] labels=source.clone();
        final int passes=detail>=.9f?0:detail>=.72f?1:detail>=.35f?2:3;
        final int radius=detail<.18f?2:1;
        for(int pass=0;pass<passes;pass++) {
            int[] next=labels.clone();
            int[] scores=new int[colors];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                int p=y*w+x, original=labels[p];if(!allowed[p]||original<0)continue;
                // M7 P3, exact: when every counted neighbour already carries the original
                // label, only that label scores, so the pixel cannot change. (original>=colors
                // still takes the full path and fails exactly as before.)
                if(original<colors&&uniform(labels,allowed,x,y,w,h,radius,original,colors))continue;
                Arrays.fill(scores,0);
                int total=0;
                for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++) {
                    int xx=x+dx,yy=y+dy;
                    if(xx<0||xx>=w||yy<0||yy>=h)continue;
                    int q=yy*w+xx;
                    if(!allowed[q]||labels[q]<0||labels[q]>=colors)continue;
                    int weight=(dx==0&&dy==0)?2:1;
                    scores[labels[q]]+=weight;total+=weight;
                }
                int winner=original;
                for(int c=0;c<colors;c++)if(scores[c]>scores[winner])winner=c;
                // Strict majority avoids driving boundaries based only on cluster index.
                if(winner!=original && scores[winner]*2>total && scores[winner]>=scores[original]+2)
                    next[p]=winner;
            }
            labels=next;
        }
        // Components are four-connected: corner-touching dots remain distinct
        // paths and do not silently turn into one self-intersecting vinyl shape.
        boolean[] seen=new boolean[labels.length];int[] queue=new int[labels.length];
        int[] borderVotes=new int[colors];
        int merged=0;
        for(int p=0;p<labels.length;p++)if(allowed[p]&&labels[p]>=0&&!seen[p]) {
            int color=labels[p],head=0,tail=1;queue[0]=p;seen[p]=true;
            Arrays.fill(borderVotes,0);
            while(head<tail) {
                int v=queue[head++],x=v%w,y=v/w;
                if(x>0)tail=visit(v-1,color,labels,allowed,seen,queue,tail,borderVotes);
                if(x+1<w)tail=visit(v+1,color,labels,allowed,seen,queue,tail,borderVotes);
                if(y>0)tail=visit(v-w,color,labels,allowed,seen,queue,tail,borderVotes);
                if(y+1<h)tail=visit(v+w,color,labels,allowed,seen,queue,tail,borderVotes);
            }
            if(tail<minimumAreaPixels) {
                int choice=-1,best=0;
                for(int c=0;c<colors;c++)if(c!=color&&borderVotes[c]>best){choice=c;best=borderVotes[c];}
                // Enclosed islands replace with surrounding owner; edge/isolated
                // regions without another color are retained, not punched out.
                if(choice>=0){for(int i=0;i<tail;i++)labels[queue[i]]=choice;merged++;}
            }
        }
        int changed=0;for(int i=0;i<labels.length;i++)if(labels[i]!=source[i])changed++;
        return new Output(labels,changed,merged);
    }
    private static boolean uniform(int[] labels,boolean[] allowed,int x,int y,int w,int h,int radius,int original,int colors){
        int y0=Math.max(0,y-radius),y1=Math.min(h-1,y+radius),x0=Math.max(0,x-radius),x1=Math.min(w-1,x+radius);
        for(int yy=y0;yy<=y1;yy++){
            int row=yy*w;
            for(int xx=x0;xx<=x1;xx++){
                int q=row+xx;
                if(!allowed[q])continue;
                int l=labels[q];
                if(l>=0&&l<colors&&l!=original)return false;
            }
        }
        return true;
    }
    private static int visit(int p,int color,int[] labels,boolean[] allowed,
                             boolean[] seen,int[] queue,int tail,int[] borders) {
        if(!allowed[p]||labels[p]<0)return tail;
        if(labels[p]==color) {
            if(!seen[p]){seen[p]=true;queue[tail++]=p;}
        } else if(labels[p]<borders.length) borders[labels[p]]++;
        return tail;
    }
}

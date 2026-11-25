package com.halftonestudio;
import java.util.Arrays;
/** Conservative photograph matte. Only a flat near-white border-connected background is removed. */
public final class VinylSubjectMask {
    private VinylSubjectMask(){}
    static boolean nearWhite(int c){
        int r=(c>>16)&255,g=(c>>8)&255,b=c&255;
        int min=Math.min(r,Math.min(g,b)),max=Math.max(r,Math.max(g,b));
        return (c>>>24)>=200&&min>=241&&max-min<=16;
    }
    public static boolean[] removePlainWhiteBackground(int[] pixels,boolean[] existing,int w,int h){
        if(pixels==null||existing==null||w<=0||h<=0||pixels.length!=(long)w*h||existing.length!=pixels.length)
            throw new IllegalArgumentException("Invalid white-background input");
        boolean[] allowed=existing.clone();int[] queue=new int[pixels.length];boolean[] seen=new boolean[pixels.length];
        int hits=0,border=0,corners=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(x==0||y==0||x==w-1||y==h-1){
            int i=y*w+x;if(!existing[i])continue;border++;
            if(nearWhite(pixels[i]))hits++;
        }
        int[] cs={0,w-1,(h-1)*w,h*w-1};
        for(int i:cs)if(existing[i]&&nearWhite(pixels[i]))corners++;
        // Refuse uncertain mattes. Photo edges containing dark scenery or solid vinyl
        // artwork must not silently lose a large amount of artwork.
        if(border==0||hits<Math.ceil(border*.60)||corners<2)return allowed;
        int head=0,tail=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(x==0||y==0||x==w-1||y==h-1){
            int i=y*w+x;
            if(existing[i]&&nearWhite(pixels[i])&&!seen[i]){seen[i]=true;queue[tail++]=i;}
        }
        while(head<tail){
            int i=queue[head++],x=i%w,y=i/w;
            if(x>0)tail=visit(i-1,pixels,existing,seen,queue,tail);
            if(x+1<w)tail=visit(i+1,pixels,existing,seen,queue,tail);
            if(y>0)tail=visit(i-w,pixels,existing,seen,queue,tail);
            if(y+1<h)tail=visit(i+w,pixels,existing,seen,queue,tail);
        }
        if(tail<pixels.length*.08)return allowed;
        for(int i=0;i<tail;i++)allowed[queue[i]]=false;
        return allowed;
    }
    private static int visit(int i,int[] pixels,boolean[] existing,boolean[] seen,int[] queue,int tail){
        if(existing[i]&&!seen[i]&&nearWhite(pixels[i])){seen[i]=true;queue[tail++]=i;}
        return tail;
    }
}

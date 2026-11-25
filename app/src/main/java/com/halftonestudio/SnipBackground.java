package com.halftonestudio;
/** Maps excluded snip regions to actual white/black input pixels (not simply a preview tint). */
public final class SnipBackground {
    private SnipBackground() {}
    public static final int EMPTY=0, WHITE=1, BLACK=2;
    public static int[] apply(int[] pixels,int width,int height,SnipMask mask,int mode){
        if(pixels==null||pixels.length!=(long)width*height||width<1||height<1)
            throw new IllegalArgumentException("Image size mismatch");
        if(mode<EMPTY||mode>BLACK)throw new IllegalArgumentException("Invalid snip background mode");
        int[] output=pixels.clone();
        if(mode==EMPTY||mask==null||mask.isEmpty())return output;
        int fill=mode==WHITE?0xffffffff:0xff000000;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            if(!mask.allows((x+.5f)/width,(y+.5f)/height))output[y*width+x]=fill;
        }
        return output;
    }
}

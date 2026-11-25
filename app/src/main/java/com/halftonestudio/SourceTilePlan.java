package com.halftonestudio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure geometry for a native-source tiled decoder. All dimensions are original
 * unrotated PHOTO pixels; halos belong to decoding, never to final geometry.
 * Consumers must transform/crop and reconcile contours in GLOBAL coordinates.
 */
public final class SourceTilePlan {
    private SourceTilePlan() {}
    public static final class Tile {
        public final int x0,y0,x1,y1,decodeX0,decodeY0,decodeX1,decodeY1;
        Tile(int a,int b,int c,int d,int p,int q,int r,int s){
            x0=a;y0=b;x1=c;y1=d;decodeX0=p;decodeY0=q;decodeX1=r;decodeY1=s;
        }
        public int coreWidth(){return x1-x0;}
        public int coreHeight(){return y1-y0;}
        public int localX(){return x0-decodeX0;}
        public int localY(){return y0-decodeY0;}
    }
    public static List<Tile> create(int w,int h,int tileSide,int halo){
        if(w<1||h<1||w>100000||h>100000||tileSide<64||tileSide>2048||halo<0||halo>64||halo*2>=tileSide)
            throw new IllegalArgumentException("Invalid native source tile dimensions");
        long count=(((long)w+tileSide-1)/tileSide)*(((long)h+tileSide-1)/tileSide);
        if(count>100000L)throw new IllegalArgumentException("Too many native source tiles");
        ArrayList<Tile> tiles=new ArrayList<>((int)count);
        for(int y=0;y<h;y+=tileSide){
            int endY=(int)Math.min((long)h,(long)y+tileSide);
            for(int x=0;x<w;x+=tileSide){
                int endX=(int)Math.min((long)w,(long)x+tileSide);
                tiles.add(new Tile(x,y,endX,endY,Math.max(0,x-halo),Math.max(0,y-halo),
                    Math.min(w,endX+halo),Math.min(h,endY+halo)));
            }
        }
        return Collections.unmodifiableList(tiles);
    }
}

package com.halftonestudio;
import java.io.IOException;
/** Header-only dimension inspection of backup thumbnails, with no image decode or allocation. */
final class ThumbnailHeader {
    private ThumbnailHeader(){}
    private static int u(byte[] b,int n){return b[n]&255;}
    private static int word(byte[] b,int n){return (u(b,n)<<8)|u(b,n+1);}
    private static long dword(byte[] b,int n){return ((long)u(b,n)<<24)|((long)u(b,n+1)<<16)|(u(b,n+2)<<8)|u(b,n+3);}
    private static void check(long w,long h) throws IOException {
        if(w<1||h<1||w>4000||h>4000||w*h>16_000_000L)
            throw new IOException("Backup preview image exceeds 4000px / 16 MP limit");
    }
    static void validate(byte[] data) throws IOException {
        if(data==null||data.length==0)return;
        if(data.length>2_000_000)throw new IOException("Backup thumbnail exceeds 2 MB limit");
        if(data.length>=24 && u(data,0)==137 && u(data,1)==80 && u(data,2)==78 && u(data,3)==71
                && u(data,4)==13&&u(data,5)==10&&u(data,6)==26&&u(data,7)==10
                && dword(data,8)==13&&u(data,12)==73&&u(data,13)==72&&u(data,14)==68&&u(data,15)==82){
            check(dword(data,16),dword(data,20));return;
        }
        if(data.length<4||u(data,0)!=255||u(data,1)!=216)throw new IOException("Unsupported preview image format");
        int at=2;
        while(at+3<data.length){
            if(u(data,at++)!=255)throw new IOException("Malformed JPEG thumbnail");
            while(at<data.length&&u(data,at)==255)at++;
            if(at>=data.length)break;
            int marker=u(data,at++);
            if(marker==0xd9||marker==0xda)break;
            if(marker==0x01||(marker>=0xd0&&marker<=0xd7))continue;
            if(at+2>data.length)break;
            int size=word(data,at);
            if(size<2||size>data.length-at)throw new IOException("Malformed JPEG segment");
            if((marker>=0xc0&&marker<=0xcf)&&marker!=0xc4&&marker!=0xc8&&marker!=0xcc){
                if(size<7)throw new IOException("Malformed JPEG dimensions");
                check(word(data,at+3),word(data,at+5));return;
            }
            at+=size;
        }
        throw new IOException("JPEG preview is missing its dimensions");
    }
}

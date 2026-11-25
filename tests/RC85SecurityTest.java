package com.halftonestudio;
import java.io.*;
import java.util.*;
import java.util.zip.*;
/** Android-free behavioral tests, deliberately independent of UI source-string checks. */
public final class RC85SecurityTest {
    static void check(boolean ok,String test){if(!ok)throw new AssertionError(test);}
    static void bad(byte[] src,String description){try{ThumbnailHeader.validate(src);throw new AssertionError(description);}catch(IOException expected){}}
    static byte[] png(int w,int h){byte[] b=new byte[24];byte[] sig={(byte)137,80,78,71,13,10,26,10};System.arraycopy(sig,0,b,0,8);b[11]=13;b[12]=73;b[13]=72;b[14]=68;b[15]=82;
        for(int i=0;i<4;i++){b[16+i]=(byte)(w >>> (24-i*8));b[20+i]=(byte)(h >>> (24-i*8));}return b;}
    static byte[] jpg(int w,int h){return new byte[]{(byte)255,(byte)216,(byte)255,(byte)192,0,11,8,(byte)(h>>>8),(byte)h,(byte)(w>>>8),(byte)w,3,1,0,17,0,2,0,17,(byte)255,(byte)217};}
    static void zipBomb() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("photo.png"));z.write(1);z.closeEntry();
            z.putNextEntry(new ZipEntry("settings.json"));byte[] data=new byte[16*1024];for(int j=0;j<65;j++)z.write(data);z.closeEntry();
        }
        try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            try{ProjectArchiveReader.read(in);throw new AssertionError("zip bomb was not bounded");}catch(IOException expected){}
        }
    }
    public static void main(String[]args) throws Exception {
        String old="content://docs/one\\ncontent://docs/two\\ncontent://docs/three";
        String five=RecentProjectUris.remember("content://docs/new",old);
        check(RecentProjectUris.kept(five).size()==4,"retained grant count");
        check(RecentProjectUris.kept(five).contains("content://docs/one"),"legacy grant migration");
        String many=RecentProjectUris.remember("content://docs/five",five);
        many=RecentProjectUris.remember("content://docs/six",many);
        check(RecentProjectUris.kept(many).size()==5,"history is bounded");
        check(!RecentProjectUris.kept(many).contains("content://docs/three"),"evicted URI is not retained");
        check(RecentProjectUris.kept(many).contains("content://docs/six"),"newest URI kept");
        check(RecentProjectUris.kept("content://a/1\nfile:///unsafe\n\nhttp://bad").size()==1,"non-content URI not treated as granted");
        ThumbnailHeader.validate(png(1800,1200));
        ThumbnailHeader.validate(jpg(128,128));
        ThumbnailHeader.validate(null);
        bad(png(4001,5),"oversize PNG accepted");
        bad(jpg(10,4096),"oversize JPEG accepted");
        ThumbnailHeader.validate(png(4000,4000)); // exact boundary is accepted
        bad(new byte[]{1,2,3},"unknown thumbnail accepted");
        bad(new byte[]{(byte)255,(byte)216,10,11},"malformed JPEG accepted");
        byte[] huge=new byte[2_000_001];bad(huge,"oversize bytes accepted");
        zipBomb();
        System.out.println("PASS RC85: permission retention, thumbnail bounds, ZIP bomb guard");
    }
}

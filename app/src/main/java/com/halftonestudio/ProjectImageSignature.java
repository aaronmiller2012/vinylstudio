package com.halftonestudio;

import java.io.IOException;
import java.util.zip.CRC32;

/** Bounded, platform-independent image structure screening. This is NOT a
 * replacement for a real Android BitmapFactory decode; it rejects corrupt or
 * mislabeled PNG/JPEG archives before the library makes them visible.
 */
final class ProjectImageSignature {
    private ProjectImageSignature(){}
    private static long u32(byte[] a,int p){return ((long)(a[p]&255)<<24)|((long)(a[p+1]&255)<<16)|((a[p+2]&255)<<8)|(a[p+3]&255);}
    static String format(byte[] bytes)throws IOException {
        if(bytes==null)throw new IOException("Missing image");
        return inspect(bytes,bytes.length);
    }
    static String inspect(byte[] bytes,long fileLength)throws IOException {
        if(bytes.length<32||fileLength<32||fileLength>ProjectArchiveReader.MAX_IMAGE_ENTRY)
            throw new IOException("Incomplete image data");
        if((bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71&&
                bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10){
            if(u32(bytes,8)!=13||bytes[12]!=73||bytes[13]!=72||bytes[14]!=68||bytes[15]!=82)
                throw new IOException("Invalid PNG IHDR chunk");
            int width=(int)u32(bytes,16),height=(int)u32(bytes,20);
            if(bytes[24]!=8 || !((bytes[25]&255)==0||(bytes[25]&255)==2||
                    (bytes[25]&255)==3||(bytes[25]&255)==4||(bytes[25]&255)==6))
                throw new IOException("Unsupported PNG bit depth or color type");
            CRC32 crc=new CRC32();crc.update(bytes,12,17);
            if(crc.getValue()!=u32(bytes,29))throw new IOException("Invalid PNG IHDR checksum");
            validateDimensions(width,height);
            return "png";
        }
        if((bytes[0]&255)==255&&(bytes[1]&255)==216){
            int pos=2;
            while(pos+4<=bytes.length){
                if((bytes[pos]&255)!=255)throw new IOException("Malformed JPEG marker");
                int marker=bytes[pos+1]&255;pos+=2;
                if(marker==0xff)continue;
                if(marker==0xd9)throw new IOException("JPEG has no image frame");
                if(marker==0xda)break; // entropy data starts; full decoder will validate it
                if(marker>=0xd0&&marker<=0xd7 || marker==0x01)continue;
                if(pos+2>bytes.length)break;
                int len=((bytes[pos]&255)<<8)|(bytes[pos+1]&255);
                if(len<2)throw new IOException("Invalid JPEG segment length");
                if((marker>=0xc0&&marker<=0xc3)||(marker>=0xc5&&marker<=0xc7)||
                    (marker>=0xc9&&marker<=0xcb)||(marker>=0xcd&&marker<=0xcf)){
                    if(pos+7>bytes.length)break;
                    int height=((bytes[pos+3]&255)<<8)|(bytes[pos+4]&255);
                    int width=((bytes[pos+5]&255)<<8)|(bytes[pos+6]&255);
                    validateDimensions(width,height);return "jpg";
                }
                pos+=len;
            }
            throw new IOException("JPEG frame header is missing or truncated");
        }
        throw new IOException("Unsupported image signature");
    }
    static void requireValidSuffix(String entryName,byte[] header,long length,byte[] tail) throws IOException {
        String actual=inspect(header,length);
        if(!entryName.endsWith("."+actual))throw new IOException("Project image extension and contents disagree");
        if(actual.equals("png")){
            // IEND = 00 00 00 00 IEND AE426082. Additional data is rejected.
            byte[] end={0,0,0,0,73,69,78,68,(byte)0xae,66,96,(byte)0x82};
            if(tail.length<12)throw new IOException("PNG missing end marker");
            for(int i=0;i<12;i++)if(tail[tail.length-12+i]!=end[i])throw new IOException("PNG is truncated or missing IEND");
        }else if(tail.length<2||(tail[tail.length-2]&255)!=255||(tail[tail.length-1]&255)!=217)
            throw new IOException("JPEG is truncated or missing EOI");
    }
    /** Streams every PNG chunk through CRC32 without buffering image pixels.
     * ZIP CRC protects the archive transport, while chunk CRC protects the PNG
     * itself; neither replaces full BitmapFactory decoding in the editor.
     */
    static final class PngChunks {
        private static final byte[] MAGIC={(byte)137,80,78,71,13,10,26,10};
        private final byte[] header=new byte[8];
        private final CRC32 crc=new CRC32();
        private int phase=0,position=0,remain=0,count=0;
        private long expectedCrc=0;
        private String type;
        private boolean ihdr,idat,iend;
        void accept(byte[] bytes,int offset,int length)throws IOException {
            for(int i=offset;i<offset+length;i++){
                int b=bytes[i]&255;
                if(iend)throw new IOException("Extra data after PNG end marker");
                switch(phase){
                    case 0: // 8-byte PNG signature
                        if(b!=(MAGIC[position]&255))throw new IOException("Invalid PNG signature");
                        if(++position==8){phase=1;position=0;}break;
                    case 1: // 4-byte big endian length followed by 4-byte type
                        header[position++]=(byte)b;
                        if(position==8){
                            long n=u32(header,0);
                            if(n>ProjectArchiveReader.MAX_IMAGE_ENTRY)throw new IOException("PNG chunk exceeds size budget");
                            remain=(int)n;
                            type=new String(header,4,4,java.nio.charset.StandardCharsets.US_ASCII);
                            if(!type.matches("[A-Za-z]{4}"))throw new IOException("Invalid PNG chunk name");
                            if(++count>120000)throw new IOException("PNG has excessive chunks");
                            if(count==1&&(!"IHDR".equals(type)||remain!=13))
                                throw new IOException("PNG does not begin with IHDR");
                            if("IHDR".equals(type)&&count!=1)throw new IOException("Duplicate PNG IHDR");
                            if("IEND".equals(type)&&(remain!=0||!ihdr||!idat))
                                throw new IOException("PNG is missing data or has invalid end marker");
                            crc.reset();crc.update(header,4,4);phase=remain==0?3:2;position=0;expectedCrc=0;
                        }break;
                    case 2: // chunk payload
                        crc.update(b);if(--remain==0){phase=3;position=0;expectedCrc=0;}break;
                    case 3: // stored chunk CRC
                        expectedCrc=(expectedCrc<<8)|b;
                        if(++position==4){
                            if(expectedCrc!=crc.getValue())throw new IOException("PNG chunk CRC mismatch: "+type);
                            if("IHDR".equals(type))ihdr=true;
                            if("IDAT".equals(type))idat=true;
                            if("IEND".equals(type))iend=true;
                            phase=1;position=0;
                        }break;
                    default:throw new IOException("Invalid PNG parser state");
                }
            }
        }
        void finish() throws IOException {
            if(!iend||!ihdr||!idat)throw new IOException("PNG data is truncated");
        }
    }
    private static void validateDimensions(int w,int h)throws IOException {
        if(w<=0||h<=0||w>6000||h>6000||(long)w*h>24_000_000L)
            throw new IOException("Project image dimensions exceed supported limits");
    }
}

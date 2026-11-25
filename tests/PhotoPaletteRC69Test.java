package com.halftonestudio;
import java.util.Arrays;
public final class PhotoPaletteRC69Test {
 private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args){
   int w=48,h=32;int[] photo=new int[w*h];
   int[] source={0xff111827,0xff89543c,0xffffc0ad,0xff00175c,0xffacbe9e,0xffbb2178,0xffcb9700,0xffffffff};
   boolean[] present=new boolean[8];
   for(int y=0;y<h;y++)for(int x=0;x<w;x++){
     int index=y*w+x;
     photo[index]=(x<8||y<6)?0x00000000:source[(x/5+y/5)%8];
     if((photo[index]>>>24)!=0)present[(x/5+y/5)%8]=true;
   }
   int[] result=PhotoPaletteExtractor.extract(photo,w,h);
   check(result.length==8,"eight source swatches");
   for(int c:result)check((c>>>24)==255,"opaque swatch");
   check(Arrays.equals(result,PhotoPaletteExtractor.extract(photo,w,h)),"deterministic");
   int[] transparent=new int[32*32];
   try{PhotoPaletteExtractor.extract(transparent,32,32);throw new AssertionError("empty alpha must reject");}
   catch(IllegalArgumentException ok){}
   int[] pale=new int[16*16];Arrays.fill(pale,0xffffffff);
   int[] whites=PhotoPaletteExtractor.extract(pale,16,16);
   for(int c:whites)check(c==0xffffffff,"White photo must retain white even on white substrate");
   System.out.println("PASS RC69 photo palette alpha, actual white, deterministic eight-color extraction");
 }
}

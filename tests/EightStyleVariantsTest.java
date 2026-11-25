package com.halftonestudio;
public class EightStyleVariantsTest {
 public static void main(String[] a){int w=160,h=170;int[] p=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int v=Math.max(0,Math.min(255,(255*x)/w));int g=(80+y)/2; p[y*w+x]=0xff000000|((v&255)<<16)|((g&255)<<8)|(v/2);}
  for(int style=0;style<8;style++)for(int variant=0;variant<(style==4?4:(style<2?1:2));variant++){
   VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=style;c.variant=variant;c.detail=48;
   long t=System.currentTimeMillis();try{StyleKit.Result r=VinylStyleSuite.generate(p,w,h,c);
   if(r.layers.isEmpty()||r.layers.size()>8)throw new AssertionError("layer count");
   for(int i=0;i<r.layers.size();i++){String s=NewStyleExport.layerSvg(r,i,true,RegistrationMarks.CROSS,2.5f,2);
   if(s.indexOf("<svg")<0||s.indexOf("<path")<0)throw new AssertionError("invalid svg "+style);
   }
   System.out.printf("%s variant %d PASS %d layers %d ms%n",VinylStyleSuite.NAMES[style],variant,r.layers.size(),System.currentTimeMillis()-t);
   }catch(IllegalArgumentException ex){if(style==2&&variant==1&&ex.getMessage().contains("ribbon")){
    System.out.println("TSP vinyl safely rejected unsafe ribbon: "+ex.getMessage());
    continue;
   }throw ex;}
  }
 }
}

package com.halftonestudio;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.List;
/** Input values chosen to be supported in both v2.24 and current engine. */
public final class ClassicV224GeometryFixture {
  static String hash(VinylEngine.Result r,int li) throws Exception {
    MessageDigest md=MessageDigest.getInstance("SHA-256");
    for(VinylEngine.Shape sh:r.shapes.get(li)) {
      if(sh.circle){md.update(String.format(Locale.ROOT,"C,%.6f,%.6f,%.6f;",sh.x,sh.y,sh.r).getBytes("UTF-8"));continue;}
      if(sh.xy!=null) for(float x:sh.xy)md.update(String.format(Locale.ROOT,"%.6f,",x).getBytes("UTF-8"));
      md.update((byte)';');
    }
    byte[] x=md.digest();StringBuilder s=new StringBuilder();for(byte b:x)s.append(String.format("%02x",b&255));return s.toString();
  }
  public static void main(String[] args) throws Exception {
    int w=84,h=100;int[] p=new int[w*h];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      float nx=(x-w*.52f)/(w*.36f),ny=(y-h*.47f)/(h*.42f);
      if(nx*nx+ny*ny>1){p[y*w+x]=0xfffaf9f6;continue;}
      int r=(int)Math.min(255,Math.max(0,135+85*Math.sin((x+5)*.09)+(y>h*.7?-45:10)));
      int g=(int)Math.min(255,Math.max(0,96+90*Math.cos((y+3)*.05)+(x<w*.4?15:0)));
      int b=(int)Math.min(255,Math.max(0,75+95*Math.sin((x+y)*.05)));
      p[y*w+x]=0xff000000 | r<<16 | g<<8 | b;
    }
    for(int style:new int[]{VinylEngine.LINE,VinylEngine.STRAIGHT,VinylEngine.HATCH,VinylEngine.CONTOUR,VinylEngine.WAVE,VinylEngine.ZIGZAG,VinylEngine.FADE,VinylEngine.RINGS,VinylEngine.RAYS,VinylEngine.BEADED,VinylEngine.CROSSHATCH,VinylEngine.BROKEN}){
      VinylEngine.Settings s=VinylEngine.Settings.defaults();s.pattern=style;
      s.widthMm=101.6f;s.marginMm=1.7f;s.registration=false;s.minimumMm=.65f;s.bridgeMm=.4f;
      if(Boolean.getBoolean("modern")) {
        s.blackColorProtection=.26f;s.shadowColorRecovery=.38f;s.portraitMidtones=.62f;
        for(VinylEngine.Layer l:s.layers)l.strength=1f;
        // The v2.52.2 engine does not declare the new toggle.
        try{s.getClass().getField("classicCmykLines").setBoolean(s,false);}catch(NoSuchFieldException ignored){}
        try{s.getClass().getField("lineContinuity").setFloat(s,.25f);}catch(NoSuchFieldException ignored){}
        try{s.getClass().getField("linePresenceRecovery").setFloat(s,.55f);}catch(NoSuchFieldException ignored){}
        try{s.getClass().getField("portraitBackgroundGuard").setFloat(s,.55f);}catch(NoSuchFieldException ignored){}
      }else{
        s.blackColorProtection=.18f;s.shadowColorRecovery=.48f;s.portraitMidtones=.82f;
        for(VinylEngine.Layer l:s.layers)l.strength=l.cmykChannel==0?1.12f:1f;
      }
      VinylEngine.Result r=VinylEngine.generate(p,w,h,s);
      for(int i=0;i<4;i++)System.out.println(style+","+i+","+r.shapes.get(i).size()+","+hash(r,i));
    }
  }
}

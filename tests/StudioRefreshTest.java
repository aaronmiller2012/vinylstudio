package com.halftonestudio;
import java.util.Arrays;
public final class StudioRefreshTest {
  public static void main(String[] args){
    SnipMask cut=new SnipMask();cut.add(new SnipMask.Operation(SnipMask.REMOVE_INSIDE,.25f,.25f,.75f,.25f,.75f,.75f,.25f,.75f));
    int w=64,h=48,n=w*h;int[] photo=new int[n];Arrays.fill(photo,0xff729ac8);
    int[] empty=SnipBackground.apply(photo,w,h,cut,SnipBackground.EMPTY);
    int[] white=SnipBackground.apply(photo,w,h,cut,SnipBackground.WHITE);
    int[] black=SnipBackground.apply(photo,w,h,cut,SnipBackground.BLACK);
    int inside=24*w+32, outside=0;
    if(empty[inside]!=photo[inside]||white[inside]!=0xffffffff||black[inside]!=0xff000000||white[outside]!=photo[outside])throw new AssertionError("Snip fill mismatch");
    if(photo[inside]!=0xff729ac8)throw new AssertionError("Modified original pixels");
    // Distinct colors in the imported photograph should create actual separate vector layers.
    int bands=8,sw=96,sh=48;int[] source=new int[sw*sh];boolean[] allowed=new boolean[source.length];
    int[] colors={0xff142e48,0xffb3413b,0xff38896e,0xffd1b355,0xff744f9e,0xffda8678,0xffa8c7da,0xffefe5d4};
    for(int y=0;y<sh;y++)for(int x=0;x<sw;x++){int i=y*sw+x;source[i]=colors[(x*bands)/sw];allowed[i]=true;}
    SourceColorStencilQuantizer.Result sep=SourceColorStencilQuantizer.quantize(source,sw,sh,bands,allowed);
    boolean[] covered=new boolean[bands];for(int o:sep.owner){if(o<0||o>=bands)throw new AssertionError("Invalid owner");covered[o]=true;}
    for(boolean yes:covered)if(!yes)throw new AssertionError("Populated input band was lost");
    MultilayerStencilEngine.Options opt=new MultilayerStencilEngine.Options();
    opt.sourceColorMode=true;opt.layerCount=bands;opt.blurRadius=0;opt.minimumIslandPixels=0;opt.registration=false;
    MultilayerStencilEngine.Result out=MultilayerStencilEngine.generate(source,sw,sh,opt);
    for(int i=0;i<bands;i++){if(out.isEmpty(i))throw new AssertionError("Missing source layer "+i);
        String svg=out.svg(i);if(!svg.contains("<path")||!svg.contains("Z\"/>")||svg.contains("<clipPath"))throw new AssertionError("Invalid cut SVG");}
    // A solid-color photo cannot honestly produce eight populated independent source colors.
    Arrays.fill(source,0xff339988);out=MultilayerStencilEngine.generate(source,sw,sh,opt);
    int nonEmpty=0;for(int i=0;i<bands;i++)if(!out.isEmpty(i))nonEmpty++;
    if(nonEmpty!=1)throw new AssertionError("Synthetic source layers appeared: "+nonEmpty);
    System.out.println("PASS: V2.27 snip black/white/empty; source-color layers, SVG geometry, truthful layer population");
  }
}

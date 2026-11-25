package com.halftonestudio;

import java.util.Arrays;
import java.util.List;

/** Targeted A4/B2/B3/B4 qualification; no device or physical-cut claims. */
public final class V3MilestoneABCompletionHostTest {
  static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
  static int[] photo(int w,int h){
    int[] out=new int[w*h];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      int v=(int)Math.max(0,Math.min(255,Math.round(238-190.*x/w-25.*y/h)));
      // Fine facial-like structure: narrow hairline, eyes and mouth.
      if(y>h/3&&y<h/3+h/25&&x>w/4&&x<3*w/4)v=20;
      if(y>h/2&&y<h/2+h/33&&x>w/3&&x<2*w/3)v=30;
      out[y*w+x]=0xff000000|(v<<16)|(v<<8)|v;
    }
    return out;
  }
  static void manufacturer(){
    check(ManufacturerVinylPalettes.COUNT==2,"Missing material references");
    for(int id=0;id<2;id++){
      ManufacturerVinylPalettes.Chip[] chips=ManufacturerVinylPalettes.chips(id);
      int[] colors=ManufacturerVinylPalettes.colors(id);
      check(chips.length==8&&colors.length==8,"Not an eight-color material reference");
      check(ManufacturerVinylPalettes.fromKey("material:"+id)==id,"Unstable material key");
      check(ManufacturerVinylPalettes.details(id).contains("physical material"),"Missing visual-vs-physical warning");
      for(int j=0;j<8;j++){
        check(colors[j]==chips[j].argb,"Code/color table drift");
        check(chips[j].code.length()>2&&!chips[j].name.isEmpty(),"Material code not identifiable");
        for(int k=0;k<j;k++)check(colors[j]!=colors[k],"Duplicate sample RGB values");
      }
      colors[0]=0;chips[0]=null;
      check(ManufacturerVinylPalettes.colors(id)[0]!=0,"Catalog mutated externally");
    }
    check(ManufacturerVinylPalettes.fromKey("material:2")==-1,"Bad material ID accepted");
    check(ManufacturerVinylPalettes.fromKey("cmyk")==-1,"CMYK collision");
  }
  static void lineSafety(){
    int w=96,h=96;int[] src=photo(w,h);
    VinylEngine.Settings s=VinylEngine.Settings.defaults();
    s.pattern=VinylEngine.LINE;s.classicCmykLines=false;s.registration=false;
    s.marginMm=1;s.minimumMm=.254f;s.widthMm=152.4f;
    for(VinylEngine.Layer l:s.layers)l.spacing=4.2f;
    VinylEngine.Result result=VinylEngine.generate(src,w,h,s);
    check(result.count()>0,"Line test unexpectedly blank");
    // One self-crossing polygon inserted into EXACT finished geometry.
    result.shapes.get(0).clear();
    result.shapes.get(0).add(VinylEngine.Shape.polygon(10,10,30,30,10,30,30,10));
    LineCutDiagnostics.Report crossing=LineCutDiagnostics.inspect(result);
    check(crossing.selfIntersections>0,"Proper crossing missed");
    // The shared cut validator deliberately throws on degenerate zero-area
    // bowties: historical Beta3 requires invalid cut paths to fail closed.
    boolean rejected=false;
    try{StandardCutSafety.inspect(result,s.minimumMm,100);}
    catch(IllegalArgumentException expected){rejected=true;}
    check(rejected,"Degenerate self-crossing polygon did not fail closed");
    result.shapes.get(0).clear();
    result.shapes.get(0).add(VinylEngine.Shape.circle(40,40,8));
    result.shapes.get(0).add(VinylEngine.Shape.circle(40,40,3));
    List<StyleCutGeometryPreflight.Warning> warnings=StandardCutSafety.inspect(result,s.minimumMm,100);
    check(warnings.stream().noneMatch(warning->warning.reason.equals("Intersecting cut contours")),
      "Properly nested concentric circle boundaries mislabeled crossing");
  }
  static void stencil(){
    check(MultilayerStencilEngine.tonalSmoothingRadius(2,.5f,800,800,152.4f,152.4f)==2,
      "Prior high-level smoothing silently changed");
    check(MultilayerStencilEngine.tonalSmoothingRadius(2,1f,800,800,152.4f,152.4f)<=
      MultilayerStencilEngine.tonalSmoothingRadius(2,1f,800,800,381f,381f),
      "High-detail physical smoothing scaling backwards");
    int w=192,h=224;int[] src=photo(w,h);
    for(float inches:new float[]{6,11,15}){
      MultilayerStencilEngine.Options o=new MultilayerStencilEngine.Options();
      o.layerCount=4;o.widthMm=inches*25.4f;o.heightMm=o.widthMm*h/(float)w;
      o.registration=false;o.detail=1f;o.blurRadius=3;
      o.autoWhiteBackground=false;o.sourceColorMode=false;
      MultilayerStencilEngine.Result a=MultilayerStencilEngine.generate(src,w,h,o);
      check(a.layers.size()==4,"Lost stencil layers at "+inches);
      for(int li=0;li<4;li++){
        MultilayerStencilEngine.Layer layer=a.layers.get(li);
        check(layer.visibleMask.length==src.length,"Broken layer dimensions");
        check(a.cutPathData(li).length()>0,"Missing physical stencil paths");
      }
      check(Math.abs(a.widthMm-inches*25.4f)<.001f,"Changed physical width");
      MultilayerStencilEngine.Result b=MultilayerStencilEngine.generate(src,w,h,o);
      for(int li=0;li<4;li++)check(a.cutPathData(li).equals(b.cutPathData(li)),
          "Nondeterministic stencil cut geometry at "+inches);
      System.out.println("B2 multilayer physical "+inches+"in, smooth radius="+
          MultilayerStencilEngine.tonalSmoothingRadius(3,1f,w,h,o.widthMm,o.heightMm));
    }
  }
  static void guide(){
    AssemblyGuidePlan.Builder builder=new AssemblyGuidePlan.Builder("B16 portrait","Photo stencil",279.4f,310f,0xffeeeeee);
    builder.registration(true,"square",4.0f);
    for(int i=0;i<8;i++)builder.addLayer(new AssemblyGuidePlan.Layer(i+1,"Sheet "+(i+1),
        ManufacturerVinylPalettes.colors(0)[i],100,true));
    AssemblyGuidePlan g=builder.build();
    AssemblyMaterialPlan m=AssemblyMaterialPlan.from(g,300f,340f);
    check(m.vinylSheets==8,"Material worksheet omitted vinyl sheet");
    check(g.layers.get(0).physicalIndex==1&&g.layers.get(7).physicalIndex==8,"Assembly order changed");
  }
  public static void main(String[] args){manufacturer();lineSafety();stencil();guide();
    System.out.println("PASS A4/B2/B3/B4 safety warnings, real material references, physical stencil detail, assembly order");}
}

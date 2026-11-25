package com.halftonestudio;
import java.util.*;
/** Mandatory production-backend test; optional independent GEOS reference mode. */
public final class V3VectorBooleanTest {
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
 static float[] rect(float x,float y,float x1,float y1){return new float[]{x,y,x1,y,x1,y1,x,y1};}
 static boolean inside(List<float[]> loops,float x,float y){boolean in=false;for(float[] p:loops)if(MaskedFacetClipper.inside(p,x,y))in=!in;return in;}
 static double area(List<float[]> loops){double a=0;for(float[] p:loops)a+=StyleKit.signedArea(p);return Math.abs(a);}
 static void exactCases(){
  VectorBooleanEngine.Session s=VectorBooleanEngine.begin(4,.254f);s.setClip(Arrays.asList(rect(0,0,50,50),rect(20,20,30,30)));
  s.add(1,rect(1,1,15,10));s.add(1,rect(10,1,25,10));s.add(1,rect(10,1,25,10));s.add(2,rect(10,1,25,10));
  s.add(3,VectorBooleanEngine.ellipse(25,25,17,17,0,s.tolerance),VectorBooleanEngine.ellipse(25,25,8,8,0,s.tolerance));
  List<float[]> a=s.finish(1,50,50),b=s.finish(2,50,50),c=s.finish(3,50,50);
  require(a.size()==1&&Math.abs(area(a)-216)<.01,"union not welded");require(b.size()==1&&Math.abs(area(b)-135)<.01,"cross-sheet contamination");require(c.size()==2&&!inside(c,25,25)&&inside(c,25,37),"ring hole lost");
  s=VectorBooleanEngine.begin(2,.254f);s.setClip(Arrays.asList(rect(0,0,50,50),rect(20,20,30,30)));s.add(1,rect(-5,-5,55,55));List<float[]> cut=s.finish(1,50,50);require(cut.size()==2&&Math.abs(area(cut)-2400)<.01,"clip/hole");
  s=VectorBooleanEngine.begin(2,.254f);s.setClip(Arrays.asList(rect(0,0,50,50)));s.add(1,rect(1,1,3,3));s.add(1,rect(10,10,12,12));require(s.finish(1,50,50).size()==2&&s.disjointOps==1,"disjoint shortcut");
  s=VectorBooleanEngine.begin(2,.254f);s.setClip(Arrays.asList(rect(0,0,50,50)));s.add(1,rect(1,1,3,3));Thread.currentThread().interrupt();try{s.finish(1,50,50);throw new AssertionError("cancel ignored");}catch(java.util.concurrent.CancellationException expected){}finally{Thread.interrupted();}
 }
 static void benchmark(){int[] photo=ConfettiWeaveRC158Test.portrait(false);int w=ConfettiWeaveRC158Test.W,h=ConfettiWeaveRC158Test.H;
  for(int layers:new int[]{4,8,9})for(float width:new float[]{101.6f,279.4f})for(int style:new int[]{VinylStyleSuite.CONFETTI_ROUND,VinylStyleSuite.CONFETTI_STREAMER,VinylStyleSuite.CHAINMAIL,VinylStyleSuite.CAMOUFLAGE+NineLayerCraftPatternEngine.FISHNET}){
   if(layers==9&&!VinylStyleSuite.isCraft(style))continue;
   VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=style;c.widthMm=width;c.detail=70;c.colorCount=layers;c.minimumMm=.254f;c.palette=ConfettiWeaveRC158Test.swatches(layers);if(layers==9)c.palette[8]=0xffbabcde;long start=System.nanoTime();StyleKit.Result r=VinylStyleSuite.generate(photo,w,h,c);long dur=(System.nanoTime()-start)/1000000;
   require(r.layers.size()==layers&&r.layers.get(0).vectorGeometryLocked,"base changed");for(int i=1;i<layers;i++)require(r.layers.get(i).vectorGeometryLocked&&!r.layers.get(i).loops.isEmpty()&&r.layers.get(i).color==c.palette[i],"missing vector sheet: "+style+" "+i);require(r.summary.contains("independent vector Boolean overlays"),"missing diagnostic");StyleKit.Result export=VinylStyleSuite.prepareForCutting(r,style,.254f,.4f);for(int i=0;i<layers;i++){require(export.layers.get(i).loops.equals(r.layers.get(i).loops),"cut geometry changed after vectorization");}System.out.printf(Locale.US,"style=%d width=%.1f layers=%d ms=%d vertices=%d%n",style,width,layers,dur,r.totalPoints());
  }
 }
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("--geos")){try(GeosVectorReferenceBackend reference=new GeosVectorReferenceBackend()){VectorBooleanEngine.install(reference);run();}}
  else{VectorBooleanEngine.install((VectorBooleanEngine.Backend)Class.forName("com.halftonestudio.JtsVectorBooleanBackend").getDeclaredConstructor().newInstance());run();}
 }
 static void duplicateSwatches(){
  VinylStyleSuite.Config c=new VinylStyleSuite.Config();c.style=VinylStyleSuite.CONFETTI_ROUND;c.widthMm=101.6f;c.detail=60;c.colorCount=4;c.minimumMm=.254f;c.palette=new int[]{0xff221100,0xffaa9977,0xffaa9977,0xffeeeeee};
  StyleKit.Result r=VinylStyleSuite.generate(ConfettiWeaveRC158Test.portrait(false),ConfettiWeaveRC158Test.W,ConfettiWeaveRC158Test.H,c);
  require(r.layers.size()==4&&r.layers.get(1).color==r.layers.get(2).color&&r.layers.get(1)!=r.layers.get(2),"duplicate swatch merged");
  for(StyleKit.Layer l:r.layers)require(l.vectorGeometryLocked&&!l.loops.isEmpty(),"layer not vectorized");
 }
 static void run(){exactCases();duplicateSwatches();ConfettiWeaveRC158Test.confettiModes();ConfettiWeaveRC158Test.chainmailTopologyAndSwatches();benchmark();System.out.println("PASS vector union, clipping, hole preservation, isolation, cancellation and style matrix");}
}

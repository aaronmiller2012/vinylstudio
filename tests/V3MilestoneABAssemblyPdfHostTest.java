package com.halftonestudio;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
public final class V3MilestoneABAssemblyPdfHostTest{
 static void check(boolean good,String reason){if(!good)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  AssemblyGuidePlan.Builder plan=new AssemblyGuidePlan.Builder("Eight sheets","Portrait",279.4f,330f,0xfff8f8f8)
   .registration(true,"squares",4.0f);
  ArrayList<AssemblyGuideDocument.LayerPage> pages=new ArrayList<>();
  ArtworkDocumentExport.Page paper=new ArtworkDocumentExport.Page(300f,350f,c->{});
  int[] measured={0};
  for(int i=0;i<8;i++){
   AssemblyGuidePlan.Layer l=new AssemblyGuidePlan.Layer(i+1,"Sheet #"+(i+1),
      ManufacturerVinylPalettes.colors(0)[i],200,true);
   plan.addLayer(l);
   if(i==0)pages.add(new AssemblyGuideDocument.LayerPage(l,paper,()->{
    measured[0]++;
    return ArtworkCutMetrics.measure(java.util.Collections.singletonList(VinylEngine.Shape.circle(20,20,2)));
   }));
   else pages.add(new AssemblyGuideDocument.LayerPage(l,paper));
  }
  AssemblyGuideDocument.Document doc=new AssemblyGuideDocument.Document(plan.build(),paper,pages);
  ByteArrayOutputStream out=new ByteArrayOutputStream();AssemblyGuideDocument.writePdf(doc,out);
  check(out.size()==1&&out.toByteArray()[0]==10,"Checklist page count not cover + checklist + eight sheets");
  check(measured[0]==1,"Real layer metrics supplier not invoked exactly once");
  boolean refused=false;
  try{
   ArrayList<AssemblyGuideDocument.LayerPage> invalid=new ArrayList<>(pages);
   invalid.set(4,new AssemblyGuideDocument.LayerPage(pages.get(4).layer,
     new ArtworkDocumentExport.Page(320f,350f,c->{})));
   new AssemblyGuideDocument.Document(plan.build(),paper,invalid);
  }catch(IllegalArgumentException expected){refused=true;}
  check(refused,"Mismatched layer board accepted");
  System.out.println("PASS Assembly PDF: cover + printable checklist + eight layers; mismatched boards rejected");
 }
}

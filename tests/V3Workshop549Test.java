package com.halftonestudio;
import android.graphics.Path;
public final class V3Workshop549Test {
 private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args){
  Path filled=new Path(new float[]{12,4,0},new boolean[]{true,true,false});
  Path pen=new Path(new float[]{7,3},new boolean[]{false,false});
  ArtworkCutMetrics.Stats s=AssemblyPathMetrics.measure(2.5,filled,pen);
  check(s.pathSummary&&s.loops==2&&s.openPaths==2&&s.lengthMm==65,"Mixed contour counts/scale");
  check(Double.isNaN(s.summedAreaMm2)&&s.regions==0&&s.holes==0,"Path summary must not infer area or topology");
  s=AssemblyPathMetrics.measure(1,new Path(new float[]{},new boolean[]{}));
  check(s.loops==0&&s.openPaths==0&&s.lengthMm==0,"Empty path");
  boolean invalid=false;try{AssemblyPathMetrics.measure(Double.NaN,filled);}catch(IllegalArgumentException e){invalid=true;}
  check(invalid,"Nonfinite scale");
  invalid=false;try{AssemblyPathMetrics.measure(1,new Path(new float[]{Float.NaN},new boolean[]{true}));}catch(IllegalArgumentException e){invalid=true;}
  check(invalid,"Nonfinite contour");
  invalid=false;try{AssemblyPathMetrics.measure(1,new Path(new float[250001],new boolean[250001]));}catch(IllegalStateException e){invalid=true;}
  check(invalid,"Contour budget");
  boolean cancelled=false;Thread.currentThread().interrupt();try{AssemblyPathMetrics.measure(1,filled);}catch(java.util.concurrent.CancellationException e){cancelled=true;}finally{Thread.interrupted();}
  check(cancelled,"Cancellation");
  ProjectReviewProgress review=new ProjectReviewProgress();long old=review.revision();
  check(review.confirm(old,4,true)&&review.checked(4),"Manual review");
  check(review.record(old,ProjectReviewProgress.DIAGNOSTIC_ZIP)&&!review.written(ProjectReviewProgress.CUT_ZIP),"Diagnostic is not a cut publication");
  check(review.record(old,ProjectReviewProgress.REFERENCE_PDF)&&!review.written(ProjectReviewProgress.ASSEMBLY_PDF),"Reference is not assembly PDF");
  review.invalidate();check(!review.checked(4)&&!review.written(ProjectReviewProgress.REFERENCE_PDF),"Invalidate reviews and documents");
  check(!review.record(old,ProjectReviewProgress.CUT_ZIP)&&!review.confirm(old,0,true),"Stale asynchronous work rejected");
  long now=review.revision();check(review.record(now,ProjectReviewProgress.CUT_ZIP)&&review.record(now,ProjectReviewProgress.ASSEMBLY_PDF),"Current published documents");
  check(review.written(ProjectReviewProgress.CUT_ZIP)&&review.written(ProjectReviewProgress.ASSEMBLY_PDF),"Current progress retained");
  System.out.println("PASS 549 assembly path measurement accounting, scale, limits, cancellation and stale publication protection");
 }
}

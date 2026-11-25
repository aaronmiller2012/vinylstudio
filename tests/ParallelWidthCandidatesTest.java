package com.halftonestudio;
public final class ParallelWidthCandidatesTest {
 public static void main(String[] args){
  double[] v=ParallelWidthCandidates.between(0,0,4,0,4,.1,0,.1,.3);
  if(v==null||Math.abs(v[4]-.1)>1e-9)throw new AssertionError();
  if(ParallelWidthCandidates.between(0,0,4,0,4,1,0,1,.3)!=null)throw new AssertionError();
  if(ParallelWidthCandidates.between(0,0,4,0,0,.1,4,.1,.3)!=null)throw new AssertionError();
  if(ParallelWidthCandidates.between(0,0,4,0,5,.1,4.01,.1,.3)!=null)throw new AssertionError();
  System.out.println("PASS opposing width candidates");
 }
}

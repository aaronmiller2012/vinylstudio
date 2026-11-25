package com.halftonestudio;
public class CutIntelligenceTest {
 public static void main(String[] args){
  if(!CutIntelligence.cannotContainMinimumDisk(.01,1)||CutIntelligence.cannotContainMinimumDisk(1,1)||CutIntelligence.cannotContainMinimumDisk(.01,0))throw new AssertionError("area bounds");
  if(!CutIntelligence.summary(new double[]{.01,2},new double[]{.01},1,1).contains("1 / 2"))throw new AssertionError("count");
  if(!ContextualLearning.matches("cut MARKS","Cut vinyl alignment marks")||ContextualLearning.matches("cut missing","cut marks"))throw new AssertionError("search");
  if(!ContextualLearning.sliderHelp("Spacing","1","5").contains("1 to 5"))throw new AssertionError("range");
  System.out.println("PASS cut intelligence area bounds and contextual help");
 }
}

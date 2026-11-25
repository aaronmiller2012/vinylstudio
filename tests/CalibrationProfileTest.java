package com.halftonestudio;
public class CalibrationProfileTest {
 public static void main(String[] args){
  String r=CalibrationRecord.set(CalibrationRecord.blank(),0,7,2,2);
  CalibrationProfile p=new CalibrationProfile(" Vinyl ☀ ","blade\n. pressure",r);
  CalibrationProfile q=CalibrationProfile.decode(CalibrationProfile.encode(p));
  if(!q.name.equals("Vinyl ☀")||!q.notes.equals(p.notes)||!q.results.equals(r))throw new AssertionError();
  for(String bad:new String[]{"", "x.y.z", "@@.YQ."+r}){try{CalibrationProfile.decode(bad);throw new AssertionError();}catch(IllegalArgumentException expected){}}
  try{new CalibrationProfile(" ","",r);throw new AssertionError();}catch(IllegalArgumentException expected){}
  System.out.println("PASS named calibration profile serialization and invalid data rejection");
 }
}

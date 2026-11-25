package com.halftonestudio;
public class CalibrationRecordTest {
 public static void main(String[] args){String s=CalibrationRecord.blank();if(!CalibrationRecord.valid(s)||CalibrationRecord.candidate(s,0)!=-1)throw new AssertionError();for(int i=3;i<8;i++)for(int j=0;j<3;j++)s=CalibrationRecord.set(s,0,i,j,2);if(CalibrationRecord.candidate(s,0)!=3||CalibrationRecord.candidate(s,1)!=-1)throw new AssertionError();s=CalibrationRecord.set(s,0,5,1,1);if(CalibrationRecord.candidate(s,0)!=6)throw new AssertionError();s=CalibrationRecord.set(s,0,7,2,0);if(CalibrationRecord.candidate(s,0)!=-1||CalibrationRecord.valid("bad"))throw new AssertionError();System.out.println("PASS calibration record isolation, missing data, contradictory results and conservative candidate limits");}
}

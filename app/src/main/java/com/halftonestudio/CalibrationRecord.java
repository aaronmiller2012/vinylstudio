package com.halftonestudio;

/** Local measurement record. No implicit application to render settings. */
public final class CalibrationRecord {
    public static final String[] ROWS={"Dots","Squares","Diamonds (diagonal)","Ribbons","Gaps","Holes"};
    public static final String[] SIZES={"0.254","0.35","0.50","0.65","0.80","1.00","1.25","1.50"};
    public static final int UNKNOWN=0, FAIL=1, PASS=2;
    private CalibrationRecord(){}
    public static String blank(){return new String(new char[ROWS.length*SIZES.length*3]).replace('\0','0');}
    public static boolean valid(String s){return s!=null&&s.length()==blank().length()&&s.matches("[012]+");}
    public static int index(int row,int size,int stage){if(row<0||row>=ROWS.length||size<0||size>=SIZES.length||stage<0||stage>=3)throw new IllegalArgumentException("Invalid specimen");return (row*SIZES.length+size)*3+stage;}
    public static String set(String s,int row,int size,int stage,int value){if(!valid(s)||value<0||value>2)throw new IllegalArgumentException("Invalid result");char[] c=s.toCharArray();c[index(row,size,stage)]=(char)('0'+value);return new String(c);}
    /** Candidate only: require all applicable stages to pass at this and every larger size. */
    public static int candidate(String s,int row){if(!valid(s))return -1;int first=-1;for(int i=SIZES.length-1;i>=0;i--){boolean pass=true;for(int j=0;j<3;j++)pass&=s.charAt(index(row,i,j))=='2';if(!pass)break;first=i;}return first;}
}
